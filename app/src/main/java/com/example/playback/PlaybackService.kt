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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var player: ExoPlayer
    private lateinit var audioAttributes: AudioAttributes
    private lateinit var extractorsFactory: DefaultExtractorsFactory

    private val codecSelector = FallbackMediaCodecSelector()
    private var extensionPreferActive: Boolean = false
    private var lastFailedCodecName: String? = null

    private val servicePlayerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            lastFailedCodecName = null
            mediaSession?.setSessionExtras(Bundle.EMPTY)
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val extracted = PlaybackDiagnostics.extractFromPlayerError(error)
            if (!extracted.failedCodecName.isNullOrBlank()) {
                lastFailedCodecName = extracted.failedCodecName
            }
            val extras = Bundle().apply {
                extracted.detailMessage?.let { putString(EXTRA_LAST_ERROR_DETAIL, it) }
                extracted.failedCodecName?.let { putString(EXTRA_LAST_FAILED_CODEC, it) }
            }
            mediaSession?.setSessionExtras(extras)
        }
    }

    override fun onCreate() {
        super.onCreate()

        val app = application as CadenceApplication
        val prefs = runBlocking { app.container.preferencesRepository.preferences.first() }

        extensionPreferActive = prefs.preferExtensionDecoders
        codecSelector.preferSoftwareOnly = prefs.preferExtensionDecoders

        trackSelector = DefaultTrackSelector(this).apply {
            SubtitleOverlayHelper.configureDefaultTextTrackSelection(
                trackSelector = this,
                enableSubtitlesByDefault = prefs.subtitlesEnabledByDefault,
            )
        }

        extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = buildExoPlayer(extensionPreferActive)

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

    private fun buildExoPlayer(preferExtensionDecoders: Boolean): ExoPlayer {
        val extensionMode = if (preferExtensionDecoders) {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        } else {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
        }

        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(extensionMode)
            setEnableDecoderFallback(true)
            setMediaCodecSelector(codecSelector)
        }

        return ExoPlayer.Builder(this, renderersFactory)
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
    }

    private fun swapPlayerIfExtensionModeChanged(targetPreferExtension: Boolean) {
        if (targetPreferExtension == extensionPreferActive) return
        extensionPreferActive = targetPreferExtension
        val oldPlayer = player
        val shuffleEnabled = oldPlayer.shuffleModeEnabled
        val repeatMode = oldPlayer.repeatMode
        val playbackParams = oldPlayer.playbackParameters
        oldPlayer.removeListener(servicePlayerListener)
        oldPlayer.stop()

        val newPlayer = buildExoPlayer(targetPreferExtension).apply {
            this.shuffleModeEnabled = shuffleEnabled
            this.repeatMode = repeatMode
            this.playbackParameters = playbackParams
        }
        player = newPlayer
        mediaSession?.setPlayer(newPlayer)
        oldPlayer.release()
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
                    val softwareFallback = args.getBoolean(ARG_SOFTWARE_FALLBACK, false)
                    codecSelector.preferSoftwareOnly = softwareFallback
                    if (args.containsKey(ARG_PREFER_EXTENSIONS)) {
                        val targetPreferExt = args.getBoolean(ARG_PREFER_EXTENSIONS, extensionPreferActive)
                        swapPlayerIfExtensionModeChanged(targetPreferExt)
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
        const val ARG_EXCLUDE_LAST_FAILED_CODEC = "exclude_last_failed_codec"
        const val ARG_RESET_EXCLUSIONS = "reset_exclusions"
        const val EXTRA_LAST_ERROR_DETAIL = "last_error_detail"
        const val EXTRA_LAST_FAILED_CODEC = "last_failed_codec"
    }
}
