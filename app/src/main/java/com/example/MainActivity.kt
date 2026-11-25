package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.MediaTrackEntity
import com.example.data.local.MediaType
import com.example.data.local.ThemeMode
import com.example.ui.CadenceViewModel
import com.example.ui.components.AddToPlaylistBottomSheet
import com.example.ui.components.AppBackgroundLayer
import com.example.ui.components.EqualizerBottomSheet
import com.example.ui.components.MacSidebar
import com.example.ui.components.MacToolbar
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.QueueBottomSheet
import com.example.ui.components.SleepTimerBottomSheet
import com.example.ui.components.SpeedPitchBottomSheet
import com.example.ui.components.ThemeStudioBottomSheet
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.PermissionGateScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.theme.CadenceTheme
import com.example.ui.theme.LocalCadenceAppearance
import kotlinx.serialization.Serializable

@Serializable data object RouteLibrary
@Serializable data object RoutePlaylists
@Serializable data object RouteSettings
@Serializable data object RouteNowPlaying
@Serializable data object RouteVideoPlayer

class MainActivity : ComponentActivity() {
  @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val appContainer = (application as CadenceApplication).container
    setContent {
      val viewModel: CadenceViewModel = viewModel(factory = CadenceViewModel.Factory(appContainer))
      val preferences by viewModel.preferences.collectAsStateWithLifecycle()
      val windowSizeClass = calculateWindowSizeClass(this)
      val darkTheme = when (preferences.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
      }
      CadenceTheme(
        darkTheme = darkTheme,
        palette = preferences.themePalette,
        dynamicColor = preferences.dynamicColorEnabled,
        backgroundPreset = preferences.backgroundPreset,
      ) {
        CadenceRoot(viewModel = viewModel, useNavRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact)
      }
    }
  }
}

@Composable
private fun CadenceRoot(viewModel: CadenceViewModel, useNavRail: Boolean) {
  val context = LocalContext.current
  val requiredMediaPermissions = remember {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
  }
  val requestPermissionsList = remember {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
  }
  var hasMediaAccess by remember { mutableStateOf(requiredMediaPermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) }
  var permissionDeniedOnce by rememberSaveable { mutableStateOf(false) }
  var initialScanTriggered by rememberSaveable { mutableStateOf(false) }
  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
    val granted = requiredMediaPermissions.all { perm -> results[perm] == true || ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED }
    hasMediaAccess = granted
    if (!granted) permissionDeniedOnce = true
  }
  LaunchedEffect(hasMediaAccess) {
    if (hasMediaAccess && !initialScanTriggered) { initialScanTriggered = true; viewModel.rescanLibrary() }
  }
  if (!hasMediaAccess) {
    PermissionGateScreen(isPermanentlyDenied = permissionDeniedOnce, onRequestPermissions = { permissionLauncher.launch(requestPermissionsList) })
    return
  }
  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentDest = backStackEntry?.destination
  val isFullPlayerRoute = currentDest?.hasRoute<RouteNowPlaying>() == true || currentDest?.hasRoute<RouteVideoPlayer>() == true
  val isVideoPlayerRoute = currentDest?.hasRoute<RouteVideoPlayer>() == true
  val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
  val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
  val audioEffectsState by viewModel.audioEffectsState.collectAsStateWithLifecycle()
  val preferences by viewModel.preferences.collectAsStateWithLifecycle()
  val playlists by viewModel.playlists.collectAsStateWithLifecycle()
  var showQueueSheet by remember { mutableStateOf(false) }
  var showEqSheet by remember { mutableStateOf(false) }
  var showSleepSheet by remember { mutableStateOf(false) }
  var showSpeedPitchSheet by remember { mutableStateOf(false) }
  var showThemeStudioSheet by remember { mutableStateOf(false) }
  var playlistTargetTrack by remember { mutableStateOf<MediaTrackEntity?>(null) }

  Box(modifier = Modifier.fillMaxSize()) {
    if (LocalCadenceAppearance.current.hasDecorativeBackground && !isVideoPlayerRoute) {
      AppBackgroundLayer(preset = preferences.backgroundPreset, overlayOpacity = preferences.backgroundOverlayOpacity, customBackgroundPath = preferences.customBackgroundPath, modifier = Modifier.fillMaxSize())
    }
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = if (isVideoPlayerRoute) MaterialTheme.colorScheme.background else Color.Transparent,
      contentWindowInsets = if (isFullPlayerRoute) WindowInsets(0, 0, 0, 0) else WindowInsets.navigationBars,
      topBar = {
        if (!isFullPlayerRoute) {
          val title = when {
            currentDest?.hasRoute<RoutePlaylists>() == true -> stringResource(R.string.nav_playlists)
            currentDest?.hasRoute<RouteSettings>() == true -> stringResource(R.string.nav_settings)
            else -> stringResource(R.string.nav_library)
          }
          Column(Modifier.statusBarsPadding()) { MacToolbar(title = title) }
        }
      },
      bottomBar = {
        if (!isFullPlayerRoute && !useNavRail) {
          Column {
            if (playbackState.currentTrack?.mediaType == MediaType.AUDIO) {
              MiniPlayerBar(playbackState = playbackState, artworkLoader = viewModel.artworkLoader, onOpenNowPlaying = { navController.navigate(RouteNowPlaying) }, onTogglePlayPause = viewModel.playbackController::togglePlayPause, onSkipNext = viewModel.playbackController::skipToNext)
            }
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)) {
              NavigationBarItem(
                selected = currentDest?.hasRoute<RouteLibrary>() == true,
                onClick = { navController.navigate(RouteLibrary) { popUpTo(RouteLibrary) { inclusive = false }; launchSingleTop = true } },
                icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_library)) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.testTag("nav_library"),
              )
              NavigationBarItem(
                selected = currentDest?.hasRoute<RoutePlaylists>() == true,
                onClick = { navController.navigate(RoutePlaylists) { popUpTo(RouteLibrary) { inclusive = false }; launchSingleTop = true } },
                icon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_playlists)) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.testTag("nav_playlists"),
              )
              NavigationBarItem(
                selected = currentDest?.hasRoute<RouteSettings>() == true,
                onClick = { navController.navigate(RouteSettings) { popUpTo(RouteLibrary) { inclusive = false }; launchSingleTop = true } },
                icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_settings)) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.testTag("nav_settings"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (!isFullPlayerRoute && useNavRail) {
          MacSidebar(
            selectedIndex = when {
              currentDest?.hasRoute<RouteLibrary>() == true -> 0
              currentDest?.hasRoute<RoutePlaylists>() == true -> 1
              else -> 2
            },
            onSelect = { idx ->
              val route: Any = when (idx) { 0 -> RouteLibrary; 1 -> RoutePlaylists; else -> RouteSettings }
              navController.navigate(route) { popUpTo(RouteLibrary) { inclusive = false }; launchSingleTop = true }
            },
          )
        }
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
          NavHost(navController = navController, startDestination = RouteLibrary, modifier = Modifier.fillMaxSize()) {
            composable<RouteLibrary> {
              val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
              val drilldown by viewModel.drilldown.collectAsStateWithLifecycle()
              val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
              val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
              val songs by viewModel.songs.collectAsStateWithLifecycle()
              val videos by viewModel.videos.collectAsStateWithLifecycle()
              val albums by viewModel.albums.collectAsStateWithLifecycle()
              val artists by viewModel.artists.collectAsStateWithLifecycle()
              val genres by viewModel.genres.collectAsStateWithLifecycle()
              val folderFilters by viewModel.folderFilters.collectAsStateWithLifecycle()
              val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
              val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
              val lastPlayed by viewModel.lastPlayed.collectAsStateWithLifecycle()
              val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
              val drilldownTracks by viewModel.drilldownTracks.collectAsStateWithLifecycle()
              LibraryScreen(
                selectedCategory = selectedCategory, drilldown = drilldown, searchQuery = searchQuery, isScanning = isScanning,
                songs = songs, videos = videos, albums = albums, artists = artists, genres = genres, folderFilters = folderFilters,
                mostPlayed = mostPlayed, recentlyAdded = recentlyAdded, lastPlayed = lastPlayed, searchResults = searchResults, drilldownTracks = drilldownTracks,
                currentPlayingId = playbackState.currentTrack?.mediaStoreId, artworkLoader = viewModel.artworkLoader,
                onSelectCategory = viewModel::selectCategory, onSearchQueryChange = viewModel::updateSearchQuery, onRescan = viewModel::rescanLibrary,
                onOpenDrilldown = viewModel::openDrilldown, onCloseDrilldown = viewModel::closeDrilldown,
                onPlayAudio = { track, queue -> viewModel.playAudio(track, queue) },
                onPlayVideo = { video -> viewModel.playVideo(video) { navController.navigate(RouteVideoPlayer) } },
                onPlayNext = viewModel.playbackController::playNextInQueue, onAddToQueue = viewModel.playbackController::addToQueue,
                onRequestAddToPlaylist = { track -> playlistTargetTrack = track }, onOpenThemeStudio = { showThemeStudioSheet = true },
                modifier = Modifier,
              )
            }
            composable<RoutePlaylists> {
              val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()
              val selectedTracks by viewModel.selectedPlaylistTracks.collectAsStateWithLifecycle()
              PlaylistsScreen(
                playlists = playlists, selectedPlaylistId = selectedPlaylistId, selectedTracks = selectedTracks, artworkLoader = viewModel.artworkLoader,
                onSelectPlaylist = viewModel::selectPlaylist, onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                onRenamePlaylist = viewModel::renamePlaylist, onDeletePlaylist = viewModel::deletePlaylist,
                onPlayTracks = { track, queue -> viewModel.playAudio(track, queue); navController.navigate(RouteNowPlaying) },
                onMoveTrack = viewModel::movePlaylistTrack, onRemoveTrack = viewModel::removeTrackFromPlaylist, modifier = Modifier,
              )
            }
            composable<RouteSettings> {
              val folderFilters by viewModel.folderFilters.collectAsStateWithLifecycle()
              SettingsScreen(
                preferences = preferences, folderFilters = folderFilters, onToggleFolderIncluded = viewModel::setFolderIncluded,
                onThemeModeChange = viewModel::setThemeMode, onThemePaletteChange = viewModel::setThemePalette, onDynamicColorChange = viewModel::setDynamicColorEnabled,
                onBackgroundPresetChange = viewModel::setBackgroundPreset, onBackgroundOverlayOpacityChange = viewModel::setBackgroundOverlayOpacity,
                onPickCustomBackgroundUri = viewModel::pickCustomBackgroundUri, onSubtitlesDefaultChange = viewModel::setSubtitlesEnabledByDefault,
                onSubtitleSizeChange = viewModel::setSubtitleTextSizeSp, onSubtitlePresetChange = viewModel::setSubtitleStylePreset,
                onPreferExtensionDecodersChange = viewModel::setPreferExtensionDecoders, modifier = Modifier,
              )
            }
            composable<RouteNowPlaying> {
              NowPlayingScreen(
                playbackState = playbackState, sleepTimerState = sleepTimerState, artworkLoader = viewModel.artworkLoader,
                onNavigateBack = { navController.popBackStack() }, onTogglePlayPause = viewModel.playbackController::togglePlayPause,
                onSeekTo = viewModel.playbackController::seekTo, onSkipPrevious = viewModel.playbackController::skipToPrevious, onSkipNext = viewModel.playbackController::skipToNext,
                onToggleShuffle = viewModel.playbackController::toggleShuffle, onCycleRepeat = viewModel.playbackController::cycleRepeatMode,
                onOpenQueue = { showQueueSheet = true }, onOpenEqualizer = { showEqSheet = true }, onOpenSpeedPitch = { showSpeedPitchSheet = true },
                onOpenSleepTimer = { showSleepSheet = true }, onOpenAddToPlaylist = { playbackState.currentTrack?.let { playlistTargetTrack = it } },
                modifier = Modifier.statusBarsPadding(),
              )
            }
            composable<RouteVideoPlayer> {
              VideoPlayerScreen(
                player = viewModel.playbackController.getPlayer(), playbackState = playbackState, preferences = preferences, sleepTimerState = sleepTimerState,
                onNavigateBack = { viewModel.playbackController.pause(); navController.popBackStack() }, onTogglePlayPause = viewModel.playbackController::togglePlayPause,
                onSeekTo = viewModel.playbackController::seekTo, onSeekBy = viewModel.playbackController::seekBy, onCycleResizeMode = viewModel.playbackController::cycleVideoResizeMode,
                onSelectSubtitleTrack = viewModel::selectSubtitleTrack, onPickSidecarSubtitleUri = viewModel::pickSidecarSubtitleUri,
                onUpdateSubtitleSize = viewModel::setSubtitleTextSizeSp, onUpdateSubtitlePreset = viewModel::setSubtitleStylePreset,
                onOpenSpeedSheet = { showSpeedPitchSheet = true }, onOpenSleepTimerSheet = { showSleepSheet = true },
              )
            }
          }
          if (!isFullPlayerRoute && useNavRail && playbackState.currentTrack?.mediaType == MediaType.AUDIO) {
            MiniPlayerBar(playbackState = playbackState, artworkLoader = viewModel.artworkLoader, onOpenNowPlaying = { navController.navigate(RouteNowPlaying) }, onTogglePlayPause = viewModel.playbackController::togglePlayPause, onSkipNext = viewModel.playbackController::skipToNext, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
          }
          playbackState.errorMessage?.let { errorMsg ->
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.errorContainer, tonalElevation = 6.dp, modifier = Modifier.align(Alignment.TopCenter).padding(14.dp).fillMaxWidth()) {
              Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(text = errorMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                if (playbackState.queue.size > 1) {
                  TextButton(onClick = { viewModel.playbackController.dismissError(); viewModel.playbackController.skipToNext() }) { Text(stringResource(R.string.playback_error_skip)) }
                }
                TextButton(onClick = viewModel.playbackController::dismissError) { Text(stringResource(R.string.playback_error_dismiss)) }
              }
            }
          }
        }
      }
    }
  }

  if (showThemeStudioSheet) {
    ThemeStudioBottomSheet(preferences = preferences, onDismiss = { showThemeStudioSheet = false }, onThemeModeChange = viewModel::setThemeMode, onThemePaletteChange = viewModel::setThemePalette, onDynamicColorChange = viewModel::setDynamicColorEnabled, onBackgroundPresetChange = viewModel::setBackgroundPreset, onBackgroundOverlayOpacityChange = viewModel::setBackgroundOverlayOpacity, onPickCustomBackgroundUri = viewModel::pickCustomBackgroundUri)
  }
  if (showQueueSheet) {
    QueueBottomSheet(queue = playbackState.queue, currentIndex = playbackState.currentIndex, artworkLoader = viewModel.artworkLoader, onDismiss = { showQueueSheet = false }, onSelectTrack = viewModel.playbackController::skipToQueueIndex, onMoveTrack = viewModel.playbackController::moveQueueItem, onRemoveTrack = viewModel.playbackController::removeQueueItem, onSaveQueueAsPlaylist = { val ids = playbackState.queue.map { it.mediaStoreId }; viewModel.createPlaylist("Queue Mix", ids); showQueueSheet = false })
  }
  if (showEqSheet) {
    EqualizerBottomSheet(state = audioEffectsState, onDismiss = { showEqSheet = false }, onToggleEnabled = viewModel.audioEffectsManager::setEnabled, onSelectPreset = viewModel.audioEffectsManager::selectPreset, onBandLevelChange = viewModel.audioEffectsManager::setBandLevel, onBassBoostChange = viewModel.audioEffectsManager::setBassBoost, onLoudnessChange = viewModel.audioEffectsManager::setLoudnessGain)
  }
  if (showSleepSheet) {
    SleepTimerBottomSheet(state = sleepTimerState, onDismiss = { showSleepSheet = false }, onSelectMinutes = viewModel.playbackController.sleepTimerManager::startMinutes, onSelectEndOfTrack = viewModel.playbackController.sleepTimerManager::setEndOfTrack, onCancel = viewModel.playbackController.sleepTimerManager::cancel)
  }
  if (showSpeedPitchSheet) {
    SpeedPitchBottomSheet(speed = playbackState.playbackSpeed, pitch = playbackState.playbackPitch, showPitchControl = playbackState.currentTrack?.mediaType != MediaType.VIDEO, onDismiss = { showSpeedPitchSheet = false }, onUpdate = viewModel.playbackController::setPlaybackParameters)
  }
  playlistTargetTrack?.let { target ->
    AddToPlaylistBottomSheet(playlists = playlists, onDismiss = { playlistTargetTrack = null }, onSelectPlaylist = { playlistId -> viewModel.addTrackToPlaylist(playlistId, target.mediaStoreId) }, onCreatePlaylist = { newName -> viewModel.createPlaylist(newName, listOf(target.mediaStoreId)) })
  }
}
