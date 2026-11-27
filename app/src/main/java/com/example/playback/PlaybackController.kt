package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.data.MediaRepository
import com.example.data.local.MediaTrackEntity
import com.example.data.local.MediaType
import com.example.data.scanner.SidecarSubtitle
import com.example.lyrics.EmbeddedLyricsExtractor
import com.example.lyrics.LyricsResult
import com.example.media.AudioEffectsManager
import com.example.media.SleepTimerManager
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VideoResizeMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    ZOOM("Zoom"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
}

data class PlaybackUiState(
    val currentTrack: MediaTrackEntity? = null,
    val queue: List<MediaTrackEntity> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
    val lyrics: LyricsResult = LyricsResult.Missing,
    val isLoadingLyrics: Boolean = false,
    val subtitleTracks: List<SubtitleTrackOption> = emptyList(),
    val activeCues: List<Cue> = emptyList(),
    val sidecarSubtitles: List<SidecarSubtitle> = emptyList(),
    val codecSummary: String = "",
    val errorMessage: String? = null,
    val videoResizeMode: VideoResizeMode = VideoResizeMode.FIT,
)

@OptIn(UnstableApi::class)
class PlaybackController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val mediaRepository: MediaRepository,
    private val lyricsExtractor: EmbeddedLyricsExtractor,
    private val audioEffectsManager: AudioEffectsManager,
) {
    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    val sleepTimerManager = SleepTimerManager(scope) {
        pause()
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var positionTickerJob: Job? = null
    private var lyricsJob: Job? = null
    private var lastCountedTrackId: Long? = null
    private var pendingStagedVideoId: Long? = null
    private var isStagedVideoItemsPrepared: Boolean = false
    private var videoSurfaceReadyForStagedId: Long? = null
    private var softwareFallbackAttemptedForTrackId: Long? = null
    private var forcedExtensionFallback: Boolean = false
    private var userPreferExtensionDecoders: Boolean? = null

    private val trackLookup = mutableMapOf<String, MediaTrackEntity>()

    fun connect() {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
            mediaController = controller
            controller.addListener(playerListener)
            syncStateFromPlayer(controller)
            startPositionTicker()
        }, MoreExecutors.directExecutor())
    }

    fun getPlayer(): Player? = mediaController

    fun playTrack(
        track: MediaTrackEntity,
        queue: List<MediaTrackEntity> = listOf(track),
        sidecarSubtitles: List<SidecarSubtitle> = emptyList(),
    ) {
        val controller = mediaController ?: return
        pendingStagedVideoId = null
        isStagedVideoItemsPrepared = false
        videoSurfaceReadyForStagedId = null
        val (validQueue, startIndex, mediaItems) = prepareQueueItems(track, queue, sidecarSubtitles)
        val prepareBlock = {
            _uiState.update {
                it.copy(
                    queue = validQueue,
                    currentIndex = startIndex,
                    currentTrack = track,
                    sidecarSubtitles = sidecarSubtitles,
                    errorMessage = null,
                )
            }
            controller.setMediaItems(mediaItems, startIndex, 0L)
            controller.prepare()
            controller.play()
            onTrackSelected(track)
        }
        resetDecoderModeForNewTrack(track.mediaStoreId, onReady = prepareBlock)
    }

    fun stageVideoTrack(
        track: MediaTrackEntity,
        queue: List<MediaTrackEntity> = listOf(track),
        sidecarSubtitles: List<SidecarSubtitle> = emptyList(),
    ) {
        val controller = mediaController ?: return
        val (validQueue, startIndex, mediaItems) = prepareQueueItems(track, queue, sidecarSubtitles)
        pendingStagedVideoId = track.mediaStoreId
        isStagedVideoItemsPrepared = false
        videoSurfaceReadyForStagedId = null
        _uiState.update {
            it.copy(
                queue = validQueue,
                currentIndex = startIndex,
                currentTrack = track,
                sidecarSubtitles = sidecarSubtitles,
                isBuffering = true,
                errorMessage = null,
            )
        }
        resetDecoderModeForNewTrack(track.mediaStoreId) {
            if (pendingStagedVideoId == track.mediaStoreId) {
                controller.setMediaItems(mediaItems, startIndex, 0L)
                isStagedVideoItemsPrepared = true
                if (videoSurfaceReadyForStagedId == track.mediaStoreId) {
                    startStagedVideoIfReady()
                }
            }
        }
    }

    fun startStagedVideoIfReady() {
        val stagedId = pendingStagedVideoId ?: return
        val controller = mediaController ?: return
        val track = _uiState.value.currentTrack ?: return
        if (track.mediaStoreId != stagedId) return
        videoSurfaceReadyForStagedId = stagedId
        if (!isStagedVideoItemsPrepared) return
        pendingStagedVideoId = null
        videoSurfaceReadyForStagedId = null
        controller.prepare()
        controller.play()
        onTrackSelected(track)
    }

    private fun prepareQueueItems(
        track: MediaTrackEntity,
        queue: List<MediaTrackEntity>,
        sidecarSubtitles: List<SidecarSubtitle>,
    ): Triple<List<MediaTrackEntity>, Int, List<MediaItem>> {
        val validQueue = if (queue.any { it.mediaStoreId == track.mediaStoreId }) queue else listOf(track)
        val startIndex = validQueue.indexOfFirst { it.mediaStoreId == track.mediaStoreId }.coerceAtLeast(0)
        validQueue.forEach { trackLookup[it.mediaStoreId.toString()] = it }
        val mediaItems = validQueue.mapIndexed { index, item ->
            val subs = if (index == startIndex && item.mediaType == MediaType.VIDEO) sidecarSubtitles else emptyList()
            buildMediaItem(item, subs)
        }
        return Triple(validQueue, startIndex, mediaItems)
    }

    fun attachSidecarSubtitleToCurrentVideo(subtitle: SidecarSubtitle) {
        val controller = mediaController ?: return
        val track = _uiState.value.currentTrack ?: return
        if (track.mediaType != MediaType.VIDEO) return

        val resumePos = controller.currentPosition.coerceAtLeast(0L)
        val wasPlaying = controller.isPlaying
        val updatedSubs = (_uiState.value.sidecarSubtitles + subtitle).distinctBy { it.uri }

        val currentIdx = controller.currentMediaItemIndex.coerceAtLeast(0)
        val updatedItem = buildMediaItem(track, updatedSubs)
        controller.replaceMediaItem(currentIdx, updatedItem)
        controller.seekTo(currentIdx, resumePos)
        controller.prepare()
        if (wasPlaying) controller.play()

        _uiState.update { it.copy(sidecarSubtitles = updatedSubs) }
    }

    fun setSidecarSubtitlesForVideo(subtitles: List<SidecarSubtitle>) {
        _uiState.update { it.copy(sidecarSubtitles = subtitles) }
    }

    fun addToQueue(track: MediaTrackEntity) {
        val controller = mediaController ?: return
        trackLookup[track.mediaStoreId.toString()] = track
        if (controller.mediaItemCount == 0) {
            playTrack(track)
            return
        }
        controller.addMediaItem(buildMediaItem(track))
        _uiState.update { it.copy(queue = it.queue + track) }
    }

    fun playNextInQueue(track: MediaTrackEntity) {
        val controller = mediaController ?: return
        trackLookup[track.mediaStoreId.toString()] = track
        if (controller.mediaItemCount == 0) {
            playTrack(track)
            return
        }
        val insertIndex = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
        controller.addMediaItem(insertIndex, buildMediaItem(track))
        val mutable = _uiState.value.queue.toMutableList()
        mutable.add(insertIndex.coerceAtMost(mutable.size), track)
        _uiState.update { it.copy(queue = mutable) }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val controller = mediaController ?: return
        val currentQueue = _uiState.value.queue.toMutableList()
        if (fromIndex !in currentQueue.indices || toIndex !in currentQueue.indices || fromIndex == toIndex) return
        controller.moveMediaItem(fromIndex, toIndex)
        val item = currentQueue.removeAt(fromIndex)
        currentQueue.add(toIndex, item)
        _uiState.update {
            it.copy(
                queue = currentQueue,
                currentIndex = controller.currentMediaItemIndex,
            )
        }
    }

    fun removeQueueItem(index: Int) {
        val controller = mediaController ?: return
        val currentQueue = _uiState.value.queue.toMutableList()
        if (index !in currentQueue.indices) return
        controller.removeMediaItem(index)
        currentQueue.removeAt(index)
        _uiState.update {
            it.copy(
                queue = currentQueue,
                currentIndex = controller.currentMediaItemIndex,
                currentTrack = currentQueue.getOrNull(controller.currentMediaItemIndex),
            )
        }
    }

    fun skipToQueueIndex(index: Int) {
        val controller = mediaController ?: return
        if (index !in 0 until controller.mediaItemCount) return
        controller.seekToDefaultPosition(index)
        controller.play()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs.coerceAtLeast(0L))
        _uiState.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    fun seekBy(deltaMs: Long) {
        val controller = mediaController ?: return
        val target = (controller.currentPosition + deltaMs).coerceIn(0L, controller.duration.coerceAtLeast(0L))
        controller.seekTo(target)
        _uiState.update { it.copy(positionMs = target) }
    }

    fun skipToNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        }
    }

    fun skipToPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3000L) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        } else {
            controller.seekTo(0L)
        }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        val next = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = next
        _uiState.update { it.copy(shuffleEnabled = next) }
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        val next = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = next
        _uiState.update { it.copy(repeatMode = next) }
    }

    fun setPlaybackParameters(speed: Float, pitch: Float) {
        val controller = mediaController ?: return
        val clampedSpeed = speed.coerceIn(0.5f, 2.5f)
        val clampedPitch = pitch.coerceIn(0.5f, 2.0f)
        controller.playbackParameters = PlaybackParameters(clampedSpeed, clampedPitch)
        _uiState.update { it.copy(playbackSpeed = clampedSpeed, playbackPitch = clampedPitch) }
    }

    fun selectSubtitleTrack(option: SubtitleTrackOption?) {
        val controller = mediaController ?: return
        SubtitleOverlayHelper.selectSubtitleTrack(controller, option)
    }

    fun setDefaultSubtitlesEnabled(enabled: Boolean) {
        val controller = mediaController ?: return
        val args = Bundle().apply { putBoolean(PlaybackService.ARG_ENABLED, enabled) }
        controller.sendCustomCommand(SessionCommand(PlaybackService.CMD_SUBTITLE_DEFAULT, Bundle.EMPTY), args)
    }

    fun setPreferExtensionDecoders(prefer: Boolean) {
        userPreferExtensionDecoders = prefer
        forcedExtensionFallback = false
        val controller = mediaController ?: return
        val args = Bundle().apply {
            putBoolean(PlaybackService.ARG_SOFTWARE_FALLBACK, prefer)
            putBoolean(PlaybackService.ARG_PREFER_EXTENSIONS, prefer)
            putBoolean(PlaybackService.ARG_RESET_EXCLUSIONS, true)
        }
        controller.sendCustomCommand(SessionCommand(PlaybackService.CMD_SET_DECODER_MODE, Bundle.EMPTY), args)
    }

    private fun resetDecoderModeForNewTrack(trackId: Long, onReady: () -> Unit) {
        val needsReset = softwareFallbackAttemptedForTrackId != null || forcedExtensionFallback
        if (softwareFallbackAttemptedForTrackId != trackId) {
            softwareFallbackAttemptedForTrackId = null
        }
        if (!needsReset) {
            onReady()
            return
        }
        val controller = mediaController
        if (controller == null) {
            onReady()
            return
        }
        val preferExt = userPreferExtensionDecoders ?: false
        val wasForcedExt = forcedExtensionFallback
        forcedExtensionFallback = false
        val args = Bundle().apply {
            putBoolean(PlaybackService.ARG_SOFTWARE_FALLBACK, preferExt)
            putBoolean(PlaybackService.ARG_RESET_EXCLUSIONS, true)
            if (wasForcedExt) {
                putBoolean(PlaybackService.ARG_PREFER_EXTENSIONS, preferExt)
            }
        }
        val future = controller.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_SET_DECODER_MODE, Bundle.EMPTY),
            args,
        )
        future.addListener({ onReady() }, androidx.core.content.ContextCompat.getMainExecutor(context))
    }

    fun cycleVideoResizeMode() {
        val modes = VideoResizeMode.entries
        val nextIdx = (modes.indexOf(_uiState.value.videoResizeMode) + 1) % modes.size
        _uiState.update { it.copy(videoResizeMode = modes[nextIdx]) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun buildMediaItem(
        track: MediaTrackEntity,
        sidecars: List<SidecarSubtitle> = emptyList(),
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .build()

        val subtitleConfigs = sidecars.map { sub ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.uri))
                .setMimeType(sub.mimeType)
                .setLanguage(sub.language)
                .setLabel(sub.displayName)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        }

        return MediaItem.Builder()
            .setMediaId(track.mediaStoreId.toString())
            .setUri(Uri.parse(track.contentUri))
            .setMediaMetadata(metadata)
            .setSubtitleConfigurations(subtitleConfigs)
            .build()
    }

    private fun onTrackSelected(track: MediaTrackEntity) {
        if (lastCountedTrackId != track.mediaStoreId) {
            lastCountedTrackId = track.mediaStoreId
            scope.launch {
                mediaRepository.recordPlayback(track.mediaStoreId)
            }
        }
        if (track.mediaType == MediaType.AUDIO) {
            loadLyricsForTrack(track)
        } else {
            _uiState.update { it.copy(lyrics = LyricsResult.Missing, isLoadingLyrics = false) }
        }
    }

    private fun loadLyricsForTrack(track: MediaTrackEntity) {
        lyricsJob?.cancel()
        _uiState.update { it.copy(isLoadingLyrics = true) }
        lyricsJob = scope.launch {
            val result = lyricsExtractor.extract(track.contentUri)
            _uiState.update {
                if (it.currentTrack?.mediaStoreId == track.mediaStoreId) {
                    it.copy(lyrics = result, isLoadingLyrics = false)
                } else {
                    it
                }
            }
        }
    }

    private fun syncStateFromPlayer(player: Player) {
        val currentId = player.currentMediaItem?.mediaId
        val track = currentId?.let { trackLookup[it] }
        _uiState.update {
            it.copy(
                currentTrack = track ?: it.currentTrack,
                currentIndex = player.currentMediaItemIndex,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.coerceAtLeast(0L),
                shuffleEnabled = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                playbackSpeed = player.playbackParameters.speed,
                playbackPitch = player.playbackParameters.pitch,
                subtitleTracks = SubtitleOverlayHelper.extractSubtitleTracks(player.currentTracks),
                codecSummary = buildCodecSummary(player.currentTracks, track ?: it.currentTrack),
            )
        }
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch(Dispatchers.Main) {
            while (true) {
                val player = mediaController
                if (player != null) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.coerceAtLeast(0L)
                    if (_uiState.value.positionMs != pos || _uiState.value.durationMs != dur) {
                        _uiState.update { it.copy(positionMs = pos, durationMs = dur) }
                    }
                }
                delay(250L)
            }
        }
    }

    private fun buildCodecSummary(tracks: Tracks, track: MediaTrackEntity?): String {
        val parts = mutableListOf<String>()
        tracks.groups.forEach { group ->
            if (group.isSelected) {
                for (i in 0 until group.length) {
                    if (group.isTrackSelected(i)) {
                        val fmt = group.getTrackFormat(i)
                        if (group.type == C.TRACK_TYPE_VIDEO) {
                            val codec = fmt.sampleMimeType?.substringAfterLast('/')?.uppercase() ?: "VIDEO"
                            val res = if (fmt.width > 0 && fmt.height > 0) "${fmt.width}x${fmt.height}" else ""
                            parts.add(listOf(codec, res).filter { it.isNotBlank() }.joinToString(" "))
                        } else if (group.type == C.TRACK_TYPE_AUDIO) {
                            val codec = fmt.sampleMimeType?.substringAfterLast('/')?.uppercase() ?: "AUDIO"
                            val sr = if (fmt.sampleRate > 0) "${fmt.sampleRate / 1000.0} kHz".replace(".0 ", " ") else ""
                            val br = when {
                                fmt.bitrate > 0 -> "${fmt.bitrate / 1000} kbps"
                                (track?.bitrateBps ?: 0) > 0 -> "${track!!.bitrateBps / 1000} kbps"
                                else -> ""
                            }
                            parts.add(listOf(codec, sr, br).filter { it.isNotBlank() }.joinToString(" • "))
                        }
                    }
                }
            }
        }
        if (parts.isEmpty() && track != null) {
            val ext = track.fileName.substringAfterLast('.', "").uppercase()
            if (ext.isNotBlank()) parts.add(ext)
            if (track.bitrateBps > 0) parts.add("${track.bitrateBps / 1000} kbps")
            if (track.sampleRateHz > 0) parts.add("${track.sampleRateHz / 1000} kHz")
        }
        return parts.joinToString("  |  ")
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _uiState.update {
                it.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    durationMs = mediaController?.duration?.coerceAtLeast(0L) ?: it.durationMs,
                )
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                sleepTimerManager.onTrackTransitionDetected()
            }
            val track = mediaItem?.mediaId?.let { trackLookup[it] }
                ?: _uiState.value.queue.getOrNull(mediaController?.currentMediaItemIndex ?: -1)
            _uiState.update {
                it.copy(
                    currentTrack = track,
                    currentIndex = mediaController?.currentMediaItemIndex ?: -1,
                    positionMs = 0L,
                    errorMessage = null,
                )
            }
            if (track != null) {
                onTrackSelected(track)
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            val subOptions = SubtitleOverlayHelper.extractSubtitleTracks(tracks)
            val codecInfo = buildCodecSummary(tracks, _uiState.value.currentTrack)
            _uiState.update {
                it.copy(
                    subtitleTracks = subOptions,
                    codecSummary = codecInfo,
                )
            }
        }

        override fun onCues(cueGroup: CueGroup) {
            _uiState.update { it.copy(activeCues = cueGroup.cues) }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            audioEffectsManager.attachToSession(audioSessionId)
        }

        override fun onPlayerError(error: PlaybackException) {
            val controller = mediaController
            val currentState = _uiState.value
            val track = currentState.currentTrack

            if (
                controller != null &&
                track != null &&
                PlaybackDiagnostics.isRecoverableCodecErrorCode(error.errorCode) &&
                softwareFallbackAttemptedForTrackId != track.mediaStoreId
            ) {
                softwareFallbackAttemptedForTrackId = track.mediaStoreId
                forcedExtensionFallback = true
                val resumePositionMs = controller.currentPosition.coerceAtLeast(0L)
                val targetIndex = controller.currentMediaItemIndex
                    .takeIf { it in currentState.queue.indices }
                    ?: currentState.currentIndex.coerceAtLeast(0)
                val subsSnapshot = currentState.sidecarSubtitles

                _uiState.update { it.copy(isBuffering = true, errorMessage = null) }

                val args = Bundle().apply {
                    putBoolean(PlaybackService.ARG_SOFTWARE_FALLBACK, true)
                    putBoolean(PlaybackService.ARG_PREFER_EXTENSIONS, true)
                    putBoolean(PlaybackService.ARG_EXCLUDE_LAST_FAILED_CODEC, true)
                }
                val future = controller.sendCustomCommand(
                    SessionCommand(PlaybackService.CMD_SET_DECODER_MODE, Bundle.EMPTY),
                    args,
                )
                future.addListener({
                    if (_uiState.value.currentTrack?.mediaStoreId == track.mediaStoreId) {
                        val (_, startIndex, mediaItems) = prepareQueueItems(
                            track = track,
                            queue = _uiState.value.queue,
                            sidecarSubtitles = subsSnapshot,
                        )
                        val resolvedIdx = targetIndex.coerceIn(0, (mediaItems.size - 1).coerceAtLeast(0))
                        controller.setMediaItems(mediaItems, if (mediaItems.isNotEmpty()) resolvedIdx else startIndex, resumePositionMs)
                        controller.prepare()
                        controller.play()
                    }
                }, androidx.core.content.ContextCompat.getMainExecutor(context))
                return
            }

            val trackName = track?.title ?: "Media file"
            val diagnosticDetail = controller?.sessionExtras?.getString(PlaybackService.EXTRA_LAST_ERROR_DETAIL)
            val fallbackTried = track != null && softwareFallbackAttemptedForTrackId == track.mediaStoreId
            val msg = PlaybackDiagnostics.formatUserErrorMessage(
                trackName = trackName,
                errorCodeName = error.errorCodeName,
                diagnosticDetail = diagnosticDetail,
                fallbackTried = fallbackTried,
            )
            _uiState.update { it.copy(errorMessage = msg, isPlaying = false, isBuffering = false) }
        }
    }

    fun release() {
        positionTickerJob?.cancel()
        lyricsJob?.cancel()
        audioEffectsManager.releaseEffects()
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }
}
