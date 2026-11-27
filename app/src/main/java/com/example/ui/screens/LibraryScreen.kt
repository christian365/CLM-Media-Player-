package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AlbumSummary
import com.example.data.local.ArtistSummary
import com.example.data.local.FolderFilterEntity
import com.example.data.local.GenreSummary
import com.example.data.local.MediaTrackEntity
import com.example.data.local.MediaType
import com.example.media.LocalArtworkLoader
import com.example.ui.LibraryCategory
import com.example.ui.LibraryDrilldown
import com.example.ui.components.AlbumArtworkThumb
import com.example.ui.components.FormatBadgePill
import com.example.ui.components.MacSegmentedStrip
import com.example.ui.components.MacWindowTitleBar
import com.example.ui.components.TrackArtworkThumb
import com.example.ui.components.formatDurationMs
import com.example.ui.components.formatExtensionTag
import com.example.ui.components.formatFileSize
import com.example.ui.theme.GlassTier
import com.example.ui.theme.LocalCadenceAppearance
import com.example.ui.theme.macGlassSurface

@Composable
fun LibraryScreen(
    selectedCategory: LibraryCategory,
    drilldown: LibraryDrilldown,
    searchQuery: String,
    isScanning: Boolean,
    songs: List<MediaTrackEntity>,
    videos: List<MediaTrackEntity>,
    albums: List<AlbumSummary>,
    artists: List<ArtistSummary>,
    genres: List<GenreSummary>,
    folderFilters: List<FolderFilterEntity>,
    mostPlayed: List<MediaTrackEntity>,
    recentlyAdded: List<MediaTrackEntity>,
    lastPlayed: List<MediaTrackEntity>,
    searchResults: List<MediaTrackEntity>,
    drilldownTracks: List<MediaTrackEntity>,
    currentPlayingId: Long?,
    artworkLoader: LocalArtworkLoader,
    onSelectCategory: (LibraryCategory) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRescan: () -> Unit,
    onOpenDrilldown: (LibraryDrilldown) -> Unit,
    onCloseDrilldown: () -> Unit,
    onPlayAudio: (MediaTrackEntity, List<MediaTrackEntity>) -> Unit,
    onPlayVideo: (MediaTrackEntity) -> Unit,
    onPlayNext: (MediaTrackEntity) -> Unit,
    onAddToQueue: (MediaTrackEntity) -> Unit,
    onRequestAddToPlaylist: (MediaTrackEntity) -> Unit,
    onOpenThemeStudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (drilldown !is LibraryDrilldown.None) {
        BackHandler(onBack = onCloseDrilldown)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // macOS Unified Window Title Bar + Search Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MacWindowTitleBar(
                title = stringResource(R.string.app_name),
                subtitle = "${songs.size} audio • ${videos.size} video • offline",
            ) {
                IconButton(
                    onClick = onOpenThemeStudio,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("open_theme_studio_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = stringResource(R.string.open_theme_studio),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                IconButton(
                    onClick = onRescan,
                    enabled = !isScanning,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("rescan_library_button"),
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.rescan_library),
                        )
                    }
                }
            }

            val hasBg = LocalCadenceAppearance.current.hasDecorativeBackground
            val searchContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (hasBg) 0.56f else 0.72f)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_placeholder),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = searchContainerColor,
                    unfocusedContainerColor = searchContainerColor,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_search_input"),
            )
        }

        if (searchQuery.isNotBlank()) {
            SearchResultsList(
                results = searchResults,
                currentPlayingId = currentPlayingId,
                artworkLoader = artworkLoader,
                onPlayAudio = { track -> onPlayAudio(track, searchResults.filter { it.mediaType == MediaType.AUDIO }) },
                onPlayVideo = onPlayVideo,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onRequestAddToPlaylist = onRequestAddToPlaylist,
            )
        } else if (drilldown !is LibraryDrilldown.None) {
            DrilldownView(
                drilldown = drilldown,
                tracks = drilldownTracks,
                currentPlayingId = currentPlayingId,
                artworkLoader = artworkLoader,
                onBack = onCloseDrilldown,
                onPlayAudio = { track -> onPlayAudio(track, drilldownTracks.filter { it.mediaType == MediaType.AUDIO }) },
                onPlayVideo = onPlayVideo,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onRequestAddToPlaylist = onRequestAddToPlaylist,
            )
        } else {
            // macOS Category Segmented Strip
            MacSegmentedStrip(
                items = LibraryCategory.entries,
                selectedItem = selectedCategory,
                labelProvider = { it.label },
                onSelect = onSelectCategory,
                testTagPrefix = "category_chip",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
            )

            when (selectedCategory) {
                LibraryCategory.SONGS -> SongsTabContent(
                    songs = songs,
                    mostPlayed = mostPlayed,
                    recentlyAdded = recentlyAdded,
                    lastPlayed = lastPlayed,
                    currentPlayingId = currentPlayingId,
                    artworkLoader = artworkLoader,
                    onPlayAudio = onPlayAudio,
                    onPlayVideo = onPlayVideo,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onRequestAddToPlaylist = onRequestAddToPlaylist,
                )
                LibraryCategory.VIDEOS -> VideosTabContent(
                    videos = videos,
                    artworkLoader = artworkLoader,
                    onPlayVideo = onPlayVideo,
                )
                LibraryCategory.ALBUMS -> AlbumsTabContent(
                    albums = albums,
                    artworkLoader = artworkLoader,
                    onOpenAlbum = { onOpenDrilldown(LibraryDrilldown.Album(it)) },
                )
                LibraryCategory.ARTISTS -> ArtistsTabContent(
                    artists = artists,
                    onOpenArtist = { onOpenDrilldown(LibraryDrilldown.Artist(it.artist)) },
                )
                LibraryCategory.GENRES -> GenresTabContent(
                    genres = genres,
                    onOpenGenre = { onOpenDrilldown(LibraryDrilldown.Genre(it.genre)) },
                )
                LibraryCategory.FOLDERS -> FoldersTabContent(
                    folders = folderFilters.filter { it.isIncluded },
                    onOpenFolder = { onOpenDrilldown(LibraryDrilldown.Folder(it.folderPath, it.folderName)) },
                )
            }
        }
    }
}

@Composable
private fun SongsTabContent(
    songs: List<MediaTrackEntity>,
    mostPlayed: List<MediaTrackEntity>,
    recentlyAdded: List<MediaTrackEntity>,
    lastPlayed: List<MediaTrackEntity>,
    currentPlayingId: Long?,
    artworkLoader: LocalArtworkLoader,
    onPlayAudio: (MediaTrackEntity, List<MediaTrackEntity>) -> Unit,
    onPlayVideo: (MediaTrackEntity) -> Unit,
    onPlayNext: (MediaTrackEntity) -> Unit,
    onAddToQueue: (MediaTrackEntity) -> Unit,
    onRequestAddToPlaylist: (MediaTrackEntity) -> Unit,
) {
    if (songs.isEmpty()) {
        EmptyLibraryCard()
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (lastPlayed.isNotEmpty() || mostPlayed.isNotEmpty() || recentlyAdded.isNotEmpty()) {
            item(key = "smart_sections") {
                SmartHighlightsSection(
                    mostPlayed = mostPlayed,
                    recentlyAdded = recentlyAdded,
                    lastPlayed = lastPlayed,
                    artworkLoader = artworkLoader,
                    onPlayAudio = { track -> onPlayAudio(track, songs) },
                    onPlayVideo = onPlayVideo,
                )
            }
        }

        item(key = "all_songs_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "All Songs (${songs.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.clickable {
                            val shuffled = songs.shuffled()
                            shuffled.firstOrNull()?.let { onPlayAudio(it, shuffled) }
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Shuffle", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        items(songs, key = { it.mediaStoreId }) { track ->
            MediaTrackRow(
                track = track,
                isPlayingCurrent = track.mediaStoreId == currentPlayingId,
                artworkLoader = artworkLoader,
                onClick = { onPlayAudio(track, songs) },
                onPlayNext = { onPlayNext(track) },
                onAddToQueue = { onAddToQueue(track) },
                onAddToPlaylist = { onRequestAddToPlaylist(track) },
            )
        }
    }
}

@Composable
private fun SmartHighlightsSection(
    mostPlayed: List<MediaTrackEntity>,
    recentlyAdded: List<MediaTrackEntity>,
    lastPlayed: List<MediaTrackEntity>,
    artworkLoader: LocalArtworkLoader,
    onPlayAudio: (MediaTrackEntity) -> Unit,
    onPlayVideo: (MediaTrackEntity) -> Unit,
) {
    var activeSmartTab by remember { mutableStateOf(0) }
    val itemsToShow = when (activeSmartTab) {
        0 -> recentlyAdded
        1 -> mostPlayed
        else -> lastPlayed
    }

    val smartLabels = listOf(
        stringResource(R.string.section_recently_added),
        stringResource(R.string.section_most_played),
        stringResource(R.string.section_last_played),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        MacSegmentedStrip(
            items = listOf(0, 1, 2),
            selectedItem = activeSmartTab,
            labelProvider = { smartLabels[it] },
            onSelect = { activeSmartTab = it },
            testTagPrefix = "smart_mix",
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(8.dp))

        if (itemsToShow.isEmpty()) {
            Text(
                text = "Play tracks to populate this smart mix.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(itemsToShow.take(12), key = { "smart_${activeSmartTab}_${it.mediaStoreId}" }) { item ->
                    val cardShape = RoundedCornerShape(16.dp)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = cardShape,
                        modifier = Modifier
                            .width(140.dp)
                            .macGlassSurface(shape = cardShape, tier = GlassTier.CARD)
                            .clickable {
                                if (item.mediaType == MediaType.VIDEO) onPlayVideo(item) else onPlayAudio(item)
                            },
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            TrackArtworkThumb(
                                loader = artworkLoader,
                                track = item,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                                cornerRadius = 12.dp,
                                showFallbackBadge = true,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (item.playCount > 0 && activeSmartTab == 1) {
                                    "${item.playCount} plays • ${item.artist}"
                                } else {
                                    item.artist
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideosTabContent(
    videos: List<MediaTrackEntity>,
    artworkLoader: LocalArtworkLoader,
    onPlayVideo: (MediaTrackEntity) -> Unit,
) {
    if (videos.isEmpty()) {
        EmptyLibraryCard()
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 168.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(videos, key = { it.mediaStoreId }) { video ->
            val cardShape = RoundedCornerShape(16.dp)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = cardShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .macGlassSurface(shape = cardShape, tier = GlassTier.CARD)
                    .clickable { onPlayVideo(video) }
                    .testTag("video_card_${video.mediaStoreId}"),
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                    ) {
                        TrackArtworkThumb(
                            loader = artworkLoader,
                            track = video,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 12.dp,
                            targetSizePx = 420,
                            showFallbackBadge = true,
                        )
                        FormatBadgePill(
                            label = formatDurationMs(video.durationMs),
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp),
                        )
                        FormatBadgePill(
                            label = formatExtensionTag(video),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp),
                        )
                    }
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val res = if (video.width > 0 && video.height > 0) "${video.width}x${video.height} • " else ""
                        Text(
                            text = "$res${formatFileSize(video.sizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumsTabContent(
    albums: List<AlbumSummary>,
    artworkLoader: LocalArtworkLoader,
    onOpenAlbum: (AlbumSummary) -> Unit,
) {
    if (albums.isEmpty()) {
        EmptyLibraryCard()
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 152.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(albums, key = { "${it.albumId}_${it.album}" }) { album ->
            val cardShape = RoundedCornerShape(16.dp)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = cardShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .macGlassSurface(shape = cardShape, tier = GlassTier.CARD)
                    .clickable { onOpenAlbum(album) },
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    AlbumArtworkThumb(
                        loader = artworkLoader,
                        albumId = album.albumId,
                        sampleContentUri = album.sampleContentUri,
                        albumName = album.album,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(album.album, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${album.artist} • ${album.trackCount} tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistsTabContent(
    artists: List<ArtistSummary>,
    onOpenArtist: (ArtistSummary) -> Unit,
) {
    if (artists.isEmpty()) {
        EmptyLibraryCard()
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(artists, key = { it.artist }) { artist ->
            val rowShape = RoundedCornerShape(14.dp)
            Surface(
                shape = rowShape,
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .macGlassSurface(shape = rowShape, tier = GlassTier.CARD)
                    .clickable { onOpenArtist(artist) },
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(artist.artist, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${artist.albumCount} albums • ${artist.trackCount} songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun GenresTabContent(
    genres: List<GenreSummary>,
    onOpenGenre: (GenreSummary) -> Unit,
) {
    if (genres.isEmpty()) {
        EmptyLibraryCard()
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(genres, key = { it.genre }) { genre ->
            val rowShape = RoundedCornerShape(14.dp)
            Surface(
                shape = rowShape,
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .macGlassSurface(shape = rowShape, tier = GlassTier.CARD)
                    .clickable { onOpenGenre(genre) },
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(genre.genre, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${genre.trackCount} tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun FoldersTabContent(
    folders: List<FolderFilterEntity>,
    onOpenFolder: (FolderFilterEntity) -> Unit,
) {
    if (folders.isEmpty()) {
        EmptyLibraryCard()
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(folders, key = { it.folderPath }) { folder ->
            val rowShape = RoundedCornerShape(14.dp)
            Surface(
                shape = rowShape,
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .macGlassSurface(shape = rowShape, tier = GlassTier.CARD)
                    .clickable { onOpenFolder(folder) },
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(folder.folderName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${folder.itemCount} items • ${folder.folderPath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun DrilldownView(
    drilldown: LibraryDrilldown,
    tracks: List<MediaTrackEntity>,
    currentPlayingId: Long?,
    artworkLoader: LocalArtworkLoader,
    onBack: () -> Unit,
    onPlayAudio: (MediaTrackEntity) -> Unit,
    onPlayVideo: (MediaTrackEntity) -> Unit,
    onPlayNext: (MediaTrackEntity) -> Unit,
    onAddToQueue: (MediaTrackEntity) -> Unit,
    onRequestAddToPlaylist: (MediaTrackEntity) -> Unit,
) {
    val headerTitle = when (drilldown) {
        is LibraryDrilldown.Album -> drilldown.summary.album
        is LibraryDrilldown.Artist -> drilldown.name
        is LibraryDrilldown.Genre -> drilldown.name
        is LibraryDrilldown.Folder -> drilldown.folderName
        LibraryDrilldown.None -> ""
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(headerTitle, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${tracks.size} items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val firstAudio = tracks.firstOrNull { it.mediaType == MediaType.AUDIO }
            if (firstAudio != null) {
                IconButton(onClick = { onPlayAudio(firstAudio) }) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play all", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
            items(tracks, key = { it.mediaStoreId }) { item ->
                MediaTrackRow(
                    track = item,
                    isPlayingCurrent = item.mediaStoreId == currentPlayingId,
                    artworkLoader = artworkLoader,
                    onClick = {
                        if (item.mediaType == MediaType.VIDEO) onPlayVideo(item) else onPlayAudio(item)
                    },
                    onPlayNext = { onPlayNext(item) },
                    onAddToQueue = { onAddToQueue(item) },
                    onAddToPlaylist = { onRequestAddToPlaylist(item) },
                )
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    results: List<MediaTrackEntity>,
    currentPlayingId: Long?,
    artworkLoader: LocalArtworkLoader,
    onPlayAudio: (MediaTrackEntity) -> Unit,
    onPlayVideo: (MediaTrackEntity) -> Unit,
    onPlayNext: (MediaTrackEntity) -> Unit,
    onAddToQueue: (MediaTrackEntity) -> Unit,
    onRequestAddToPlaylist: (MediaTrackEntity) -> Unit,
) {
    if (results.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No matching local files.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
        items(results, key = { it.mediaStoreId }) { item ->
            MediaTrackRow(
                track = item,
                isPlayingCurrent = item.mediaStoreId == currentPlayingId,
                artworkLoader = artworkLoader,
                onClick = {
                    if (item.mediaType == MediaType.VIDEO) onPlayVideo(item) else onPlayAudio(item)
                },
                onPlayNext = { onPlayNext(item) },
                onAddToQueue = { onAddToQueue(item) },
                onAddToPlaylist = { onRequestAddToPlaylist(item) },
            )
        }
    }
}

@Composable
fun MediaTrackRow(
    track: MediaTrackEntity,
    isPlayingCurrent: Boolean,
    artworkLoader: LocalArtworkLoader,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TrackArtworkThumb(
            loader = artworkLoader,
            track = track,
            modifier = Modifier.size(50.dp),
            cornerRadius = 11.dp,
            targetSizePx = 128,
            showFallbackBadge = track.mediaType == MediaType.VIDEO,
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isPlayingCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                FormatBadgePill(label = formatExtensionTag(track))
            }
            Text(
                text = if (track.mediaType == MediaType.VIDEO) {
                    "${track.folderName} • ${formatDurationMs(track.durationMs)}"
                } else {
                    "${track.artist} • ${track.album}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = formatDurationMs(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (track.mediaType == MediaType.AUDIO) {
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Track options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Play Next") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onPlayNext()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Queue") },
                        leadingIcon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onAddToQueue()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Playlist") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onAddToPlaylist()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        val emptyShape = RoundedCornerShape(22.dp)
        Surface(
            shape = emptyShape,
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .macGlassSurface(shape = emptyShape, tier = GlassTier.PANEL),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Album,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                )
                Text(
                    text = stringResource(R.string.empty_library_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.empty_library_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
