package com.example.data

import com.example.data.local.AlbumSummary
import com.example.data.local.ArtistSummary
import com.example.data.local.CadenceDao
import com.example.data.local.FolderFilterEntity
import com.example.data.local.GenreSummary
import com.example.data.local.MediaTrackEntity
import com.example.data.local.MediaType
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.local.PlaylistWithCount
import com.example.data.scanner.MediaStoreScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MediaRepository(
    private val dao: CadenceDao,
    private val scanner: MediaStoreScanner,
) {
    private val scanMutex = Mutex()
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val audioTracks: Flow<List<MediaTrackEntity>> = dao.observeIncludedByType(MediaType.AUDIO)
    val videoTracks: Flow<List<MediaTrackEntity>> = dao.observeIncludedByType(MediaType.VIDEO)
    val albums: Flow<List<AlbumSummary>> = dao.observeAlbums()
    val artists: Flow<List<ArtistSummary>> = dao.observeArtists()
    val genres: Flow<List<GenreSummary>> = dao.observeGenres()
    val folderFilters: Flow<List<FolderFilterEntity>> = dao.observeFolderFilters()
    val playlists: Flow<List<PlaylistWithCount>> = dao.observePlaylists()
    val mostPlayed: Flow<List<MediaTrackEntity>> = dao.observeMostPlayed()
    val recentlyAdded: Flow<List<MediaTrackEntity>> = dao.observeRecentlyAdded()
    val lastPlayed: Flow<List<MediaTrackEntity>> = dao.observeLastPlayed()

    fun searchMedia(query: String): Flow<List<MediaTrackEntity>> = dao.searchIncludedMedia(query)
    fun observeAlbumTracks(albumId: Long): Flow<List<MediaTrackEntity>> = dao.observeTracksByAlbum(albumId)
    fun observeArtistTracks(artist: String): Flow<List<MediaTrackEntity>> = dao.observeTracksByArtist(artist)
    fun observeGenreTracks(genre: String): Flow<List<MediaTrackEntity>> = dao.observeTracksByGenre(genre)
    fun observeFolderTracks(folderPath: String): Flow<List<MediaTrackEntity>> = dao.observeTracksByFolder(folderPath)
    fun observePlaylistTracks(playlistId: Long): Flow<List<MediaTrackEntity>> = dao.observePlaylistTracks(playlistId)

    suspend fun getTrackById(mediaStoreId: Long): MediaTrackEntity? =
        withContext(Dispatchers.IO) { dao.getTrackById(mediaStoreId) }

    suspend fun rescanLibrary(): Int = scanMutex.withLock {
        _isScanning.value = true
        try {
            withContext(Dispatchers.IO) {
                val scanOutput = scanner.scanAll()
                val existingStats = dao.getTrackStatsSnapshots().associateBy { it.mediaStoreId }
                val existingFolders = dao.getFolderFiltersSnapshot().associateBy { it.folderPath }

                // Merge scanned items with existing user stats (playCount, lastPlayedAtMs, cached bitrate/sampleRate)
                val mergedTracks = scanOutput.tracks.map { scanned ->
                    val prev = existingStats[scanned.mediaStoreId]
                    if (prev != null) {
                        scanned.copy(
                            playCount = prev.playCount,
                            lastPlayedAtMs = prev.lastPlayedAtMs,
                            bitrateBps = if (scanned.bitrateBps > 0) scanned.bitrateBps else prev.bitrateBps,
                            sampleRateHz = if (scanned.sampleRateHz > 0) scanned.sampleRateHz else prev.sampleRateHz,
                        )
                    } else {
                        scanned
                    }
                }

                // Sync tracks without wiping playlists or play counts
                if (mergedTracks.isEmpty()) {
                    dao.deleteAllTracks()
                } else {
                    // Chunked upsert & pruning to respect SQLite bind parameter limits on very large libraries
                    mergedTracks.chunked(400).forEach { chunk ->
                        dao.upsertTracks(chunk)
                    }
                    val activeIds = mergedTracks.map { it.mediaStoreId }
                    if (activeIds.size <= 900) {
                        dao.deleteTracksNotIn(activeIds)
                    } else {
                        val activeSet = activeIds.toHashSet()
                        val staleIds = existingStats.keys.filter { it !in activeSet }
                        staleIds.chunked(500).forEach { chunk ->
                            dao.deleteTracksByIds(chunk)
                        }
                    }
                }

                // Sync folder filters while preserving user include/exclude choices
                val mergedFolders = scanOutput.folderCounts.map { (path, pair) ->
                    val (name, count) = pair
                    val prevFilter = existingFolders[path]
                    FolderFilterEntity(
                        folderPath = path,
                        folderName = name,
                        isIncluded = prevFilter?.isIncluded ?: true,
                        itemCount = count,
                    )
                }
                if (mergedFolders.isNotEmpty()) {
                    dao.upsertFolderFilters(mergedFolders)
                    val activePaths = mergedFolders.map { it.folderPath }
                    if (activePaths.size <= 900) {
                        dao.deleteFolderFiltersNotIn(activePaths)
                    }
                }

                mergedTracks.size
            }
        } finally {
            _isScanning.value = false
        }
    }

    suspend fun recordPlayback(mediaStoreId: Long) = withContext(Dispatchers.IO) {
        dao.incrementPlayCount(mediaStoreId, System.currentTimeMillis())
        val track = dao.getTrackById(mediaStoreId)
        if (track != null && track.mediaType == MediaType.AUDIO &&
            (track.bitrateBps == 0 || track.sampleRateHz == 0)
        ) {
            scanner.enrichTechnicalAudioMetadata(track.contentUri)?.let { (bitrate, sampleRate) ->
                if (bitrate > 0 || sampleRate > 0) {
                    dao.updateTechnicalAudioMetadata(
                        mediaStoreId = mediaStoreId,
                        bitrateBps = if (bitrate > 0) bitrate else track.bitrateBps,
                        sampleRateHz = if (sampleRate > 0) sampleRate else track.sampleRateHz,
                    )
                }
            }
        }
    }

    suspend fun setFolderIncluded(folderPath: String, included: Boolean) = withContext(Dispatchers.IO) {
        dao.setFolderIncluded(folderPath, included)
    }

    suspend fun createPlaylist(name: String, initialTrackIds: List<Long> = emptyList()): Long =
        withContext(Dispatchers.IO) {
            val playlistId = dao.insertPlaylist(PlaylistEntity(name = name.trim().ifBlank { "New Playlist" }))
            initialTrackIds.forEachIndexed { index, trackId ->
                dao.insertPlaylistTrack(
                    PlaylistTrackCrossRef(
                        playlistId = playlistId,
                        mediaStoreId = trackId,
                        sortOrder = index,
                    )
                )
            }
            playlistId
        }

    suspend fun renamePlaylist(playlistId: Long, name: String) = withContext(Dispatchers.IO) {
        if (name.isNotBlank()) {
            dao.renamePlaylist(playlistId, name.trim())
        }
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        dao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, mediaStoreId: Long) = withContext(Dispatchers.IO) {
        val nextOrder = dao.getMaxPlaylistSortOrder(playlistId) + 1
        dao.insertPlaylistTrack(
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                mediaStoreId = mediaStoreId,
                sortOrder = nextOrder,
            )
        )
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, mediaStoreId: Long) = withContext(Dispatchers.IO) {
        dao.removePlaylistTrack(playlistId, mediaStoreId)
    }

    suspend fun reorderPlaylist(playlistId: Long, orderedTrackIds: List<Long>) = withContext(Dispatchers.IO) {
        dao.reorderPlaylist(playlistId, orderedTrackIds)
    }
}
