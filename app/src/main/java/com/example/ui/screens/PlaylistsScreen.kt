package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.MediaTrackEntity
import com.example.data.local.PlaylistWithCount
import com.example.media.LocalArtworkLoader
import com.example.ui.components.TrackArtworkThumb
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.LocalCadenceAppearance

@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistWithCount>,
    selectedPlaylistId: Long?,
    selectedPlaylistTracks: List<MediaTrackEntity>,
    artworkLoader: LocalArtworkLoader,
    onSelectPlaylist: (Long?) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (Long, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onPlayTracks: (MediaTrackEntity, List<MediaTrackEntity>) -> Unit,
    onMoveTrack: (Long, List<MediaTrackEntity>, Int, Int) -> Unit,
    onRemoveTrack: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PlaylistWithCount?>(null) }

    if (selectedPlaylistId != null) {
        BackHandler { onSelectPlaylist(null) }
    }

    val hasBg = LocalCadenceAppearance.current.hasDecorativeBackground
    val cardColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (hasBg) 0.88f else 1f)

    Box(modifier = modifier.fillMaxSize()) {
        if (selectedPlaylistId == null) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Playlists", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${playlists.size} custom local playlist${if (playlists.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = cardColor,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.PlaylistPlay,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp),
                                )
                                Text("No playlists yet", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Create offline playlists that survive library rescans.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(onClick = { showCreateDialog = true }) {
                                    Text("Create Playlist")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(playlists, key = { it.playlist.playlistId }) { item ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = cardColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPlaylist(item.playlist.playlistId) }
                                    .testTag("playlist_card_${item.playlist.playlistId}"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(46.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.PlaylistPlay,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.playlist.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${item.trackCount} track${if (item.trackCount == 1) "" else "s"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { renameTarget = item }) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Rename playlist")
                                    }
                                    IconButton(onClick = { onDeletePlaylist(item.playlist.playlistId) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete playlist")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 92.dp)
                    .testTag("create_playlist_fab"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "New playlist")
            }
        } else {
            val activePlaylist = playlists.firstOrNull { it.playlist.playlistId == selectedPlaylistId }
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { onSelectPlaylist(null) },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to playlists")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activePlaylist?.playlist?.name ?: "Playlist",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${selectedPlaylistTracks.size} tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (selectedPlaylistTracks.isNotEmpty()) {
                        OutlinedButton(onClick = {
                            val shuffled = selectedPlaylistTracks.shuffled()
                            onPlayTracks(shuffled.first(), shuffled)
                        }) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Shuffle")
                        }
                        Spacer(Modifier.width(6.dp))
                        Button(onClick = {
                            onPlayTracks(selectedPlaylistTracks.first(), selectedPlaylistTracks)
                        }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Play")
                        }
                    }
                }

                if (selectedPlaylistTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "This playlist is empty. Add songs from the Library menu.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(selectedPlaylistTracks, key = { _, t -> t.mediaStoreId }) { index, track ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = cardColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayTracks(track, selectedPlaylistTracks) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TrackArtworkThumb(
                                        loader = artworkLoader,
                                        track = track,
                                        modifier = Modifier.size(46.dp),
                                        cornerRadius = 10.dp,
                                        targetSizePx = 128,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${track.artist} • ${formatDurationMs(track.durationMs)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    IconButton(
                                        onClick = { onMoveTrack(selectedPlaylistId, selectedPlaylistTracks, index, index - 1) },
                                        enabled = index > 0,
                                    ) {
                                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up")
                                    }
                                    IconButton(
                                        onClick = { onMoveTrack(selectedPlaylistId, selectedPlaylistTracks, index, index + 1) },
                                        enabled = index < selectedPlaylistTracks.lastIndex,
                                    ) {
                                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down")
                                    }
                                    IconButton(onClick = { onRemoveTrack(selectedPlaylistId, track.mediaStoreId) }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Remove from playlist")
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(100.dp)) }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        PlaylistNameDialog(
            title = "Create Playlist",
            initialName = "",
            confirmLabel = "Create",
            onDismiss = { showCreateDialog = false },
            onConfirm = {
                onCreatePlaylist(it)
                showCreateDialog = false
            },
        )
    }

    renameTarget?.let { target ->
        PlaylistNameDialog(
            title = "Rename Playlist",
            initialName = target.playlist.name,
            confirmLabel = "Save",
            onDismiss = { renameTarget = null },
            onConfirm = {
                onRenamePlaylist(target.playlist.playlistId, it)
                renameTarget = null
            },
        )
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Playlist name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
