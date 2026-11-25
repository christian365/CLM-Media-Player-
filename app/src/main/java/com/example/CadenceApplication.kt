package com.example

import android.app.Application
import com.example.data.MediaRepository
import com.example.data.local.CadenceDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.scanner.MediaStoreScanner
import com.example.data.scanner.SidecarSubtitleFinder
import com.example.lyrics.EmbeddedLyricsExtractor
import com.example.media.AudioEffectsManager
import com.example.media.LocalArtworkLoader
import com.example.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(application: Application) {
    val appContext: Application = application
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database: CadenceDatabase = CadenceDatabase.getInstance(application)
    val scanner: MediaStoreScanner = MediaStoreScanner(application)
    val sidecarSubtitleFinder: SidecarSubtitleFinder = SidecarSubtitleFinder(application)
    val mediaRepository: MediaRepository = MediaRepository(database.cadenceDao(), scanner)
    val preferencesRepository: PreferencesRepository = PreferencesRepository(application)
    val artworkLoader: LocalArtworkLoader = LocalArtworkLoader(application)
    val lyricsExtractor: EmbeddedLyricsExtractor = EmbeddedLyricsExtractor(application)
    val audioEffectsManager: AudioEffectsManager = AudioEffectsManager()
    val playbackController: PlaybackController = PlaybackController(
        context = application,
        scope = appScope,
        mediaRepository = mediaRepository,
        lyricsExtractor = lyricsExtractor,
        audioEffectsManager = audioEffectsManager,
    )
}

class CadenceApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.playbackController.connect()
    }
}
