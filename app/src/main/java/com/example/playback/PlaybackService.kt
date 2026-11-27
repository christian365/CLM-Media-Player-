package com.example.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.CadenceApplication
import com.example.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var player: ExoPlayer

    private val codecSelector = FallbackMediaCodecSelector()
    private var lastFailedCodecName: String? = null

    private val servicePlayerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            lastFailedCodecName = null
            runCatching { mediaSession?.setSessionExtras(Bundle.EMPTY) }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val extracted = PlaybackDiagnostics.extractFromPlayerError(error)
            if (!extracted.failedCodecName.isNullOrBlank()) {
                lastFailedCodecName = extracted.failedCodecName
            }
            val extras = Bundle().apply {
                extracted.detailMessage?.let { putString(EXTRA_LAST_ERROR_DETAIL, it) }
                extracted.failedCodecName?.let { putString(EXTRA_LAST_FAILED_CODEC, it) }
                putBoolean(EXTRA_LAST_ERROR_IS_AUDIO, extracted.isAudioFailure)
            }
            runCatching { mediaSession?.setSessionExtras(extras) }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val app = application as CadenceApplication
        val prefs = runBlocking { app.container.preferencesRepository.preferences.first() }

        codecSelector.preferFfmpegAudio = prefs.preferExtensionDecoders
        codecSelector.preferFfmpegVideo = prefs.preferExtensionDecoders
        codecSelector.preferSoftwareOnly = false

        val renderersFactory = NextRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            setEnableDecoderFallback(true)
            setMediaCodecSelector(codecSelector)
        }

        trackSelector = DefaultTrackSelector(this).apply {
            SubtitleOverlayHelper.configureDefaultTextTrackSelection(
                trackSelector = this,
                enableSubtitlesByDefault = prefs.subtitlesEnabledByDefault,
            )
        }

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this, extractorsFactory)
            )
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build().apply {
                addListener(servicePlayerListener)
            }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 1001, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build())

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(CadenceSessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = mediaSession?.player
        if (currentPlayer == null || !currentPlayer.playWhenReady || currentPlayer.mediaItemCount == 0 || currentPlayer.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.removeListener(servicePlayerListener)
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private inner class CadenceSessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_SUBTITLE_DEFAULT, Bundle.EMPTY))
                .add(SessionCommand(CMD_SET_DECODER_MODE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(sessionCommands).build()
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            return when (customCommand.customAction) {
                CMD_SUBTITLE_DEFAULT -> {
                    SubtitleOverlayHelper.configureDefaultTextTrackSelection(trackSelector, args.getBoolean(ARG_ENABLED, true))
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CMD_SET_DECODER_MODE -> {
                    if (args.getBoolean(ARG_RESET_EXCLUSIONS, false)) {
                        codecSelector.clearExcludedCodecs()
                        lastFailedCodecName = null
                    }
                    if (args.getBoolean(ARG_EXCLUDE_LAST_FAILED_CODEC, false)) {
                        codecSelector.excludeCodec(lastFailedCodecName)
                    }
                    codecSelector.preferSoftwareOnly = args.getBoolean(ARG_SOFTWARE_FALLBACK, false)
                    if (args.containsKey(ARG_PREFER_EXTENSIONS)) {
                        val preferAll = args.getBoolean(ARG_PREFER_EXTENSIONS, false)
                        codecSelector.preferFfmpegAudio = preferAll
                        codecSelector.preferFfmpegVideo = preferAll
                    }
                    if (args.containsKey(ARG_PREFER_FFMPEG_VIDEO)) {
                        codecSelector.preferFfmpegVideo = args.getBoolean(ARG_PREFER_FFMPEG_VIDEO, false)
                    }
                    if (args.containsKey(ARG_PREFER_FFMPEG_AUDIO)) {
                        codecSelector.preferFfmpegAudio = args.getBoolean(ARG_PREFER_FFMPEG_AUDIO, false)
                    }
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                else -> super.onCustomCommand(session, controller, customCommand, args)
            }
        }
    }

    companion object {
        const val CMD_SUBTITLE_DEFAULT = "com.example.playback.CMD_SUBTITLE_DEFAULT"
        const val CMD_SET_DECODER_MODE = "com.example.playback.CMD_SET_DECODER_MODE"
        const val ARG_ENABLED = "enabled"
        const val ARG_SOFTWARE_FALLBACK = "software_fallback"
        const val ARG_PREFER_EXTENSIONS = "prefer_extensions"
        const val ARG_PREFER_FFMPEG_VIDEO = "prefer_ffmpeg_video"
        const val ARG_PREFER_FFMPEG_AUDIO = "prefer_ffmpeg_audio"
        const val ARG_EXCLUDE_LAST_FAILED_CODEC = "exclude_last_failed_codec"
        const val ARG_RESET_EXCLUSIONS = "reset_exclusions"
        const val EXTRA_LAST_ERROR_DETAIL = "last_error_detail"
        const val EXTRA_LAST_FAILED_CODEC = "last_failed_codec"
        const val EXTRA_LAST_ERROR_IS_AUDIO = "last_error_is_audio"
    }
}
