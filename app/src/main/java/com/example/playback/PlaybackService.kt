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

    override fun onCreate() {
        super.onCreate()

        val app = application as CadenceApplication
        val prefs = runBlocking { app.container.preferencesRepository.preferences.first() }

        val extensionMode = if (prefs.preferExtensionDecoders) {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        } else {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
        }

        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(extensionMode)
            setEnableDecoderFallback(true)
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
            .build()

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
        mediaSession?.run { player.release(); release(); mediaSession = null }
        super.onDestroy()
    }

    private inner class CadenceSessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(SessionCommand(CMD_SUBTITLE_DEFAULT, Bundle.EMPTY)).build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(sessionCommands).build()
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CMD_SUBTITLE_DEFAULT) {
                SubtitleOverlayHelper.configureDefaultTextTrackSelection(trackSelector, args.getBoolean(ARG_ENABLED, true))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    companion object {
        const val CMD_SUBTITLE_DEFAULT = "com.example.playback.CMD_SUBTITLE_DEFAULT"
        const val ARG_ENABLED = "enabled"
    }
}
