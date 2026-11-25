package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CadenceDao {

    // ---- Sync / Upsert preserving user stats & playlists ----

    @Query("SELECT mediaStoreId, playCount, lastPlayedAtMs, bitrateBps, sampleRateHz FROM media_tracks")
    suspend fun getTrackStatsSnapshots(): List<TrackStatsSnapshot>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<MediaTrackEntity>)

    @Query("DELETE FROM media_tracks WHERE mediaStoreId NOT IN (:activeIds)")
    suspend fun deleteTracksNotIn(activeIds: List<Long>)

    @Query("DELETE FROM media_tracks WHERE mediaStoreId IN (:ids)")
    suspend fun deleteTracksByIds(ids: List<Long>)

    @Query("DELETE FROM media_tracks")
    suspend fun deleteAllTracks()

    @Query("UPDATE media_tracks SET playCount = playCount + 1, lastPlayedAtMs = :playedAtMs WHERE mediaStoreId = :mediaStoreId")
    suspend fun incrementPlayCount(mediaStoreId: Long, playedAtMs: Long)

    @Query("UPDATE media_tracks SET bitrateBps = :bitrateBps, sampleRateHz = :sampleRateHz WHERE mediaStoreId = :mediaStoreId")
    suspend fun updateTechnicalAudioMetadata(mediaStoreId: Long, bitrateBps: Int, sampleRateHz: Int)

    // ---- Included media queries (filtered by folder_filters) ----

    @Query(
        """
        SELECT t.* FROM media_tracks t
        LEFT JOIN folder_filters f ON t.folderPath = f.folderPath
        WHERE COALESCE(f.isIncluded, 1) = 1 AND t.mediaType = :type
        ORDER BY t.title COLLATE NOCASE ASC
        """
    )
    fun observeIncludedByType(type: MediaType): Flow<List<MediaTrackEntity>>

    @Query(
        """
        SELECT t.* FROM media_tracks t
        LEFT JOIN folder_filters f ON t.folderPath = f.folderPath
        WHERE COALESCE(f.isIncluded, 1) = 1
          AND (
            t.title LIKE '%' || :query || '%'
            OR t.artist LIKE '%' || :query || '%'
            OR t.album LIKE '%' || :query || '%'
            OR t.folderPath LIKE '%' || :query || '%'
            OR t.fileName LIKE '%' || :query || '%'
          )
        ORDER BY t.mediaType ASC, t.title COLLATE NOCASE ASC
        """
    )
    fun searchIncludedMedia(query: String): Flow<List<MediaTrackEntity>>

    @Query(
        """
        SELECT t.* FROM media_tracks t
        LEFT JOIN folder_filters f ON t.folderPath = f.folderPath
        WHERE COALESCE(f.isIncluded, 1) = 1 AND t.playCount > 0
        ORDER BY t.playCount DESC, t.lastPlayedAtMs DESC
        LIMIT :limit
        """
    )
    fun observeMostPlayed(limit: Int = 20): Flow<List<MediaTrackEntity>>

    @Query(
        """
        SELECT t.* FROM media_tracks t
        LEFT JOIN folder_filters f ON t.folderPath = f.folderPath
        WHERE COALESCE(f.isIncluded, 1) = 1
        ORDER BY t.dateAddedSec DESC
        LIMIT :limit
        """
    )
    fun observeRecentlyAdded(limit: Int = 20): Flow<List<MediaTrackEntity>>

    @Query(
        """
        SELECT t.* FROM media_tracks t
        LEFT JOIN folder_filters f ON t.folderPath = f.folderPath
        WHERE COALESCE(f.isIncluded, 1) = 1 AND t.lastPlayedAtMs IS NOT NULL
        ORDER BY t.lastPlayedAtMs DESC
        LIMIT :limit
        """
    )
    fun observeLastPlayed(limit: Int = 20): Flow<List<MediaTrackEntity>>

    @Query(
        """
        SELECT t.albumId AS albumId, t.album AS album, MIN(t.artist) AS artist,
               COUNT(*) AS trackCount, SUM(t.durationMs) AS totalDurationMs,
               MIN(t.mediaStoreId) AS sampleTrackId, MIN(t.contentUri) AS sampleContentUri
        FROM media_tracks t
        LEFT JOIN folder_filters f ON t.folderPath = f.folderPath
        WHERE COALESCE(f.isIncluded, 1) = 1 AND t.mediaType = 'AUDIO'
        GROUP BY t.albumId, t.album
        ORDER BY t.album COLLATE NOCASE ASC
        """
    )
    fun observeAlbums(): Flow<List<AlbumSummary>>

    @Query(
        """
        SELECT t.artist AS artist, COUNT(*) AS trackCount, COUNT(DISTINCT t.albumId) AS albumCount
        FROM media_tracks t
        LEFT JOIN folder_filters f ON t.folderPath = f.folderPath
        WHERE COALESCE(f.isIncluded, 1) = 1 AND t.mediaType = 'AUDIO'
        GROUP BY t.artist
        ORDER BY t.artist COLLATE NOCASE ASC
        """
    )
    fun observeArtists(): Flow<List<ArtistSummary>>

    @Query(
        """
        SELECT t.genre AS genre, COUNT(*) AS trackCount
        FROM media_tracks t
        LEFT JOIN folder_filters f ON t.folderPath = f.folderPath
        WHERE COALESCE(f.isIncluded, 1) = 1 AND t.mediaType = 'AUDIO' AND t.genre != ''
        GROUP BY t.genre
        ORDER BY t.genre COLLATE NOCASE ASC
        """
    )
    fun observeGenres(): Flow<List<GenreSummary>>

    @Query(
        """
        SELECT t.* FROM media_tracks t
        LEFT JOIN folder_filters f ON t.folderPath = f.folderPath
        WHERE COALESCE(f.isIncluded, 1) = 1 AND t.mediaType = 'AUDIO' AND t.albumId = :albumId
        ORDER BY t.trackNumber ASC, t.title COLLATE NOCASE ASC
        """
    )
    fun observeTracksByAlbum(albumId: Long): Flow<List<MediaTrackEntity>>

    @Query(
        """
        SELECT t.* FROM media_tracks t
        LEFT JOIN folder_filters f ON t.folderPath = f.folderPath
        WHERE COALESCE(f.isIncluded, 1) = 1 AND t.mediaType = 'AUDIO' AND t.artist = :artist
        ORDER BY t.album COLLATE NOCASE ASC, t.trackNumber ASC, t.title COLLATE NOCASE ASC
        """
    )
    fun observeTracksByArtist(artist: String): Flow<List<MediaTrackEntity>>

    @Query(
        """
        SELECT t.* FROM media_tracks t
        LEFT JOIN folder_filters f ON t.folderPath = f.folderPath
        WHERE COALESCE(f.isIncluded, 1) = 1 AND t.mediaType = 'AUDIO' AND t.genre = :genre
        ORDER BY t.title COLLATE NOCASE ASC
        """
    )
    fun observeTracksByGenre(genre: String): Flow<List<MediaTrackEntity>>

    @Query(
        """
        SELECT t.* FROM media_tracks t
        WHERE t.folderPath = :folderPath
        ORDER BY t.mediaType ASC, t.title COLLATE NOCASE ASC
        """
    )
    fun observeTracksByFolder(folderPath: String): Flow<List<MediaTrackEntity>>

    @Query("SELECT * FROM media_tracks WHERE mediaStoreId = :mediaStoreId LIMIT 1")
    suspend fun getTrackById(mediaStoreId: Long): MediaTrackEntity?

    // ---- Folder Filters ----

    @Query("SELECT * FROM folder_filters ORDER BY folderName COLLATE NOCASE ASC")
    fun observeFolderFilters(): Flow<List<FolderFilterEntity>>

    @Query("SELECT * FROM folder_filters")
    suspend fun getFolderFiltersSnapshot(): List<FolderFilterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolderFilters(filters: List<FolderFilterEntity>)

    @Query("DELETE FROM folder_filters WHERE folderPath NOT IN (:activePaths)")
    suspend fun deleteFolderFiltersNotIn(activePaths: List<String>)

    @Query("UPDATE folder_filters SET isIncluded = :included WHERE folderPath = :folderPath")
    suspend fun setFolderIncluded(folderPath: String, included: Boolean)

    // ---- Playlists ----

    @Query(
        """
        SELECT p.*, COUNT(pt.mediaStoreId) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_tracks pt ON p.playlistId = pt.playlistId
        GROUP BY p.playlistId
        ORDER BY p.createdAtMs DESC
        """
    )
    fun observePlaylists(): Flow<List<PlaylistWithCount>>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE playlistId = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String)

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query(
        """
        SELECT t.* FROM media_tracks t
        INNER JOIN playlist_tracks pt ON t.mediaStoreId = pt.mediaStoreId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.sortOrder ASC
        """
    )
    fun observePlaylistTracks(playlistId: Long): Flow<List<MediaTrackEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getMaxPlaylistSortOrder(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistTrack(crossRef: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND mediaStoreId = :mediaStoreId")
    suspend fun removePlaylistTrack(playlistId: Long, mediaStoreId: Long)

    @Query("UPDATE playlist_tracks SET sortOrder = :sortOrder WHERE playlistId = :playlistId AND mediaStoreId = :mediaStoreId")
    suspend fun updatePlaylistTrackOrder(playlistId: Long, mediaStoreId: Long, sortOrder: Int)

    @Transaction
    suspend fun reorderPlaylist(playlistId: Long, orderedTrackIds: List<Long>) {
        orderedTrackIds.forEachIndexed { index, trackId ->
            updatePlaylistTrackOrder(playlistId, trackId, index)
        }
    }
}
