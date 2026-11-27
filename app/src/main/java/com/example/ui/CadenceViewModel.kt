package com.example.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.MediaRepository
import com.example.data.local.AlbumSummary
import com.example.data.local.ArtistSummary
import com.example.data.local.BackgroundPreset
import com.example.data.local.FolderFilterEntity
import com.example.data.local.GenreSummary
import com.example.data.local.MediaTrackEntity
import com.example.data.local.MediaType
import com.example.data.local.PlaylistWithCount
import com.example.data.local.SubtitleStylePreset
import com.example.data.local.ThemeMode
import com.example.data.local.ThemePalette
import com.example.data.local.UserPreferences
import com.example.data.scanner.SidecarSubtitle
import com.example.data.scanner.SidecarSubtitleFinder
import com.example.media.AudioEffectsManager
import com.example.media.AudioEffectsState
import com.example.media.BackgroundImageStorage
import com.example.media.LocalArtworkLoader
import com.example.media.SleepTimerState
import com.example.online.CodecAdviceReport
import com.example.online.MediaQuerySanitizer
import com.example.online.OnlineCodecAdvisor
import com.example.online.OnlineSubtitleCandidate
import com.example.online.OnlineSubtitleDownloadResult
import com.example.online.OnlineSubtitleSearchResult
import com.example.online.OnlineSubtitleService
import com.example.playback.PlaybackController
import com.example.playback.PlaybackUiState
import com.example.playback.SubtitleTrackOption
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnlineSubtitlesUiState(
    val isSearching: Boolean = false,
    val isDownloading: Boolean = false,
    val results: List<OnlineSubtitleCandidate> = emptyList(),
    val statusMessage: String? = null,
)

data class CodecAdvisorUiState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val report: CodecAdviceReport? = null,
)

enum class LibraryCategory(val label: String) {
    SONGS("Songs"),
    VIDEOS("Videos"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    GENRES("Genres"),
    FOLDERS("Folders"),
}

sealed interface LibraryDrilldown {
    data object None : LibraryDrilldown
    data class Album(val summary: AlbumSummary) : LibraryDrilldown
    data class Artist(val name: String) : LibraryDrilldown
    data class Genre(val name: String) : LibraryDrilldown
    data class Folder(val folderPath: String, val folderName: String) : LibraryDrilldown
}

@OptIn(ExperimentalCoroutinesApi::class)
class CadenceViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val repository: MediaRepository = container.mediaRepository
    private val preferencesRepo = container.preferencesRepository
    private val sidecarFinder: SidecarSubtitleFinder = container.sidecarSubtitleFinder
    private val onlineSubtitleService: OnlineSubtitleService = container.onlineSubtitleService
    private val onlineCodecAdvisor: OnlineCodecAdvisor = container.onlineCodecAdvisor

    val artworkLoader: LocalArtworkLoader = container.artworkLoader
    val playbackController: PlaybackController = container.playbackController
    val audioEffectsManager: AudioEffectsManager = container.audioEffectsManager

    val playbackState: StateFlow<PlaybackUiState> = playbackController.uiState
    val sleepTimerState: StateFlow<SleepTimerState> = playbackController.sleepTimerManager.state
    val audioEffectsState: StateFlow<AudioEffectsState> = audioEffectsManager.state
    val isScanning: StateFlow<Boolean> = repository.isScanning

    private val _onlineSubtitlesState = MutableStateFlow(OnlineSubtitlesUiState())
    val onlineSubtitlesState: StateFlow<OnlineSubtitlesUiState> = _onlineSubtitlesState.asStateFlow()

    private val _codecAdvisorState = MutableStateFlow(CodecAdvisorUiState())
    val codecAdvisorState: StateFlow<CodecAdvisorUiState> = _codecAdvisorState.asStateFlow()

    val preferences: StateFlow<UserPreferences> = preferencesRepo.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    private val _selectedCategory = MutableStateFlow(LibraryCategory.SONGS)
    val selectedCategory: StateFlow<LibraryCategory> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _drilldown = MutableStateFlow<LibraryDrilldown>(LibraryDrilldown.None)
    val drilldown: StateFlow<LibraryDrilldown> = _drilldown.asStateFlow()

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId: StateFlow<Long?> = _selectedPlaylistId.asStateFlow()

    val songs: StateFlow<List<MediaTrackEntity>> = repository.audioTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videos: StateFlow<List<MediaTrackEntity>> = repository.videoTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<AlbumSummary>> = repository.albums
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<ArtistSummary>> = repository.artists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val genres: StateFlow<List<GenreSummary>> = repository.genres
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folderFilters: StateFlow<List<FolderFilterEntity>> = repository.folderFilters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistWithCount>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayed: StateFlow<List<MediaTrackEntity>> = repository.mostPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyAdded: StateFlow<List<MediaTrackEntity>> = repository.recentlyAdded
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lastPlayed: StateFlow<List<MediaTrackEntity>> = repository.lastPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchResults: StateFlow<List<MediaTrackEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList()) else repository.searchMedia(query.trim())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val drilldownTracks: StateFlow<List<MediaTrackEntity>> = _drilldown
        .flatMapLatest { dd ->
            when (dd) {
                LibraryDrilldown.None -> flowOf(emptyList())
                is LibraryDrilldown.Album -> repository.observeAlbumTracks(dd.summary.albumId)
                is LibraryDrilldown.Artist -> repository.observeArtistTracks(dd.name)
                is LibraryDrilldown.Genre -> repository.observeGenreTracks(dd.name)
                is LibraryDrilldown.Folder -> repository.observeFolderTracks(dd.folderPath)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedPlaylistTracks: StateFlow<List<MediaTrackEntity>> = _selectedPlaylistId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.observePlaylistTracks(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun rescanLibrary() {
        viewModelScope.launch {
            repository.rescanLibrary()
        }
    }

    fun selectCategory(category: LibraryCategory) {
        _selectedCategory.value = category
        _drilldown.value = LibraryDrilldown.None
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openDrilldown(target: LibraryDrilldown) {
        _drilldown.value = target
    }

    fun closeDrilldown() {
        _drilldown.value = LibraryDrilldown.None
    }

    fun selectPlaylist(playlistId: Long?) {
        _selectedPlaylistId.value = playlistId
    }

    fun playAudio(track: MediaTrackEntity, queue: List<MediaTrackEntity>) {
        playbackController.playTrack(track = track, queue = queue)
    }

    fun playVideo(video: MediaTrackEntity, onReady: () -> Unit = {}) {
        _onlineSubtitlesState.value = OnlineSubtitlesUiState()
        viewModelScope.launch {
            val sidecars = sidecarFinder.findMatchingSubtitles(video)
            playbackController.stageVideoTrack(
                track = video,
                queue = listOf(video),
                sidecarSubtitles = sidecars,
            )
            onReady()
        }
    }

    fun pickSidecarSubtitleUri(uri: Uri) {
        viewModelScope.launch {
            val sub = sidecarFinder.resolvePickedSubtitle(uri) ?: return@launch
            playbackController.attachSidecarSubtitleToCurrentVideo(sub)
        }
    }

    fun selectSubtitleTrack(option: SubtitleTrackOption?) {
        playbackController.selectSubtitleTrack(option)
    }

    fun defaultSubtitleSearchQueryFor(track: MediaTrackEntity?): String {
        if (track == null) return ""
        return MediaQuerySanitizer.cleanVideoSearchQuery(track.title, track.fileName)
    }

    fun searchOnlineSubtitles(query: String, languageCode: String) {
        if (!preferences.value.onlineSubtitlesEnabled) {
            _onlineSubtitlesState.value = OnlineSubtitlesUiState(
                statusMessage = "Online subtitle search is disabled in Settings.",
            )
            return
        }
        viewModelScope.launch {
            _onlineSubtitlesState.update {
                it.copy(isSearching = true, statusMessage = null, results = emptyList())
            }
            when (
                val res = onlineSubtitleService.search(
                    query = query,
                    languageCode = languageCode,
                    openSubtitlesApiKey = preferences.value.openSubtitlesApiKey,
                )
            ) {
                is OnlineSubtitleSearchResult.Success -> {
                    _onlineSubtitlesState.update {
                        it.copy(
                            isSearching = false,
                            results = res.items,
                            statusMessage = if (res.items.isEmpty()) "No matching online subtitles found." else null,
                        )
                    }
                }
                OnlineSubtitleSearchResult.Offline -> {
                    _onlineSubtitlesState.update {
                        it.copy(
                            isSearching = false,
                            statusMessage = "No internet connection. Connect online or load a local .srt/.ass/.vtt file.",
                        )
                    }
                }
                is OnlineSubtitleSearchResult.Error -> {
                    _onlineSubtitlesState.update {
                        it.copy(
                            isSearching = false,
                            statusMessage = "Subtitle search failed: ${res.message}",
                        )
                    }
                }
            }
        }
    }

    fun downloadOnlineSubtitle(
        candidate: OnlineSubtitleCandidate,
        onAttached: () -> Unit = {},
    ) {
        viewModelScope.launch {
            _onlineSubtitlesState.update { it.copy(isDownloading = true, statusMessage = null) }
            when (
                val res = onlineSubtitleService.downloadAndSave(
                    candidate = candidate,
                    openSubtitlesApiKey = preferences.value.openSubtitlesApiKey,
                )
            ) {
                is OnlineSubtitleDownloadResult.Success -> {
                    playbackController.attachSidecarSubtitleToCurrentVideo(res.sidecar)
                    _onlineSubtitlesState.update {
                        it.copy(isDownloading = false, statusMessage = "Loaded: ${res.sidecar.displayName}")
                    }
                    onAttached()
                }
                OnlineSubtitleDownloadResult.Offline -> {
                    _onlineSubtitlesState.update {
                        it.copy(isDownloading = false, statusMessage = "Offline — cannot download subtitle.")
                    }
                }
                is OnlineSubtitleDownloadResult.Error -> {
                    _onlineSubtitlesState.update {
                        it.copy(isDownloading = false, statusMessage = res.message)
                    }
                }
            }
        }
    }

    fun openCodecAdvisor() {
        val track = playbackState.value.currentTrack
        val detail = playbackState.value.lastErrorDiagnosticDetail
        val allowOnline = preferences.value.onlineCodecLookupEnabled
        _codecAdvisorState.value = CodecAdvisorUiState(isVisible = true, isLoading = true, report = null)
        viewModelScope.launch {
            val ext = track?.fileName?.substringAfterLast('.', "").orEmpty()
            val report = onlineCodecAdvisor.inspectAndAdvise(
                trackTitle = track?.title ?: "Media file",
                diagnosticDetail = detail,
                fileExtension = ext,
                allowOnlineLookup = allowOnline,
            )
            _codecAdvisorState.update { it.copy(isLoading = false, report = report) }
        }
    }

    fun dismissCodecAdvisor() {
        _codecAdvisorState.value = CodecAdvisorUiState()
    }

    fun fetchOnlineLyrics() {
        playbackController.fetchOnlineLyrics(forceRefresh = true)
    }

    fun createPlaylist(name: String, initialTrackIds: List<Long> = emptyList()) {
        viewModelScope.launch {
            repository.createPlaylist(name, initialTrackIds)
        }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        viewModelScope.launch {
            repository.renamePlaylist(playlistId, name)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            if (_selectedPlaylistId.value == playlistId) {
                _selectedPlaylistId.value = null
            }
            repository.deletePlaylist(playlistId)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, mediaStoreId: Long) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, mediaStoreId)
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, mediaStoreId: Long) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, mediaStoreId)
        }
    }

    fun movePlaylistTrack(playlistId: Long, orderedTracks: List<MediaTrackEntity>, from: Int, to: Int) {
        if (from !in orderedTracks.indices || to !in orderedTracks.indices || from == to) return
        val mutable = orderedTracks.toMutableList()
        val moved = mutable.removeAt(from)
        mutable.add(to, moved)
        viewModelScope.launch {
            repository.reorderPlaylist(playlistId, mutable.map { it.mediaStoreId })
        }
    }

    fun setFolderIncluded(folderPath: String, included: Boolean) {
        viewModelScope.launch {
            repository.setFolderIncluded(folderPath, included)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepo.setThemeMode(mode) }
    }

    fun setThemePalette(palette: ThemePalette) {
        viewModelScope.launch { preferencesRepo.setThemePalette(palette) }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setDynamicColorEnabled(enabled) }
    }

    fun setBackgroundPreset(preset: BackgroundPreset) {
        viewModelScope.launch { preferencesRepo.setBackgroundPreset(preset) }
    }

    fun setBackgroundOverlayOpacity(opacity: Float) {
        viewModelScope.launch { preferencesRepo.setBackgroundOverlayOpacity(opacity) }
    }

    fun pickCustomBackgroundUri(uri: Uri) {
        viewModelScope.launch {
            val savedPath = BackgroundImageStorage.importPickedImage(container.appContext, uri)
            if (savedPath != null) {
                preferencesRepo.setCustomBackgroundPath(savedPath)
            }
        }
    }

    fun setSubtitlesEnabledByDefault(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepo.setSubtitlesEnabledByDefault(enabled)
            playbackController.setDefaultSubtitlesEnabled(enabled)
        }
    }

    fun setSubtitleTextSizeSp(sizeSp: Float) {
        viewModelScope.launch { preferencesRepo.setSubtitleTextSizeSp(sizeSp) }
    }

    fun setSubtitleStylePreset(preset: SubtitleStylePreset) {
        viewModelScope.launch { preferencesRepo.setSubtitleStylePreset(preset) }
    }

    fun setPreferExtensionDecoders(prefer: Boolean) {
        viewModelScope.launch {
            preferencesRepo.setPreferExtensionDecoders(prefer)
            playbackController.setPreferExtensionDecoders(prefer)
        }
    }

    fun setOnlineLyricsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setOnlineLyricsEnabled(enabled) }
    }

    fun setOnlineSubtitlesEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setOnlineSubtitlesEnabled(enabled) }
    }

    fun setOnlineCodecLookupEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setOnlineCodecLookupEnabled(enabled) }
    }

    fun setOpenSubtitlesApiKey(apiKey: String) {
        viewModelScope.launch { preferencesRepo.setOpenSubtitlesApiKey(apiKey) }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CadenceViewModel(container) as T
        }
    }
}
