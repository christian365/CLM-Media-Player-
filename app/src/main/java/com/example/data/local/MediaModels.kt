package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class MediaType {
    AUDIO,
    VIDEO,
}

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
}

enum class ThemePalette(val label: String) {
    SKY_BLUE("Sky Blue"),
    SUNSET_CORAL("Sunset Coral"),
    AURORA_MINT("Aurora Mint"),
    ELECTRIC_VIOLET("Electric Violet"),
    GOLDEN_AMBER("Golden Amber"),
    CRIMSON_BERRY("Crimson Berry"),
    OCEAN_TEAL("Ocean Teal"),
    OBSIDIAN_SLATE("Obsidian Slate"),
}

enum class BackgroundPreset(val label: String) {
    THEME_SOLID("Solid"),
    THEME_GLOW("Theme Glow"),
    SKY_HORIZON("Sky Horizon"),
    COTTON_CANDY("Cotton Candy"),
    NORTHERN_LIGHTS("Aurora Wave"),
    SUNSET_PRISM("Sunset Prism"),
    WALLPAPER_SKY_CLOUDS("Sky Clouds"),
    WALLPAPER_AURORA_WAVES("Silk Waves"),
    CUSTOM_PHOTO("Custom Photo"),
}

enum class SubtitleStylePreset(val label: String) {
    CLASSIC_OUTLINE("Classic Outline"),
    HIGH_CONTRAST_BOX("High-Contrast Box"),
    AMBER_CINEMA("Amber Cinema"),
    MINIMAL_TRANSLUCENT("Minimal Translucent"),
}

class CadenceTypeConverters {
    @TypeConverter
    fun fromMediaType(value: MediaType): String = value.name

    @TypeConverter
    fun toMediaType(value: String): MediaType =
        runCatching { MediaType.valueOf(value) }.getOrDefault(MediaType.AUDIO)
}

@Entity(
    tableName = "media_tracks",
    indices = [
        Index(value = ["mediaType"]),
        Index(value = ["folderPath"]),
        Index(value = ["albumId"]),
        Index(value = ["playCount"]),
        Index(value = ["dateAddedSec"]),
    ],
)
data class MediaTrackEntity(
    @PrimaryKey val mediaStoreId: Long,
    val contentUri: String,
    val mediaType: MediaType,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val genre: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
    val mimeType: String,
    val folderPath: String,
    val folderName: String,
    val fileName: String,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val bitrateBps: Int = 0,
    val sampleRateHz: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val playCount: Int = 0,
    val lastPlayedAtMs: Long? = null,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0L,
    val name: String,
    val createdAtMs: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "mediaStoreId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MediaTrackEntity::class,
            parentColumns = ["mediaStoreId"],
            childColumns = ["mediaStoreId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["mediaStoreId"]), Index(value = ["playlistId", "sortOrder"])],
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val mediaStoreId: Long,
    val sortOrder: Int,
    val addedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "folder_filters")
data class FolderFilterEntity(
    @PrimaryKey val folderPath: String,
    val folderName: String,
    val isIncluded: Boolean = true,
    val itemCount: Int = 0,
)

data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    val trackCount: Int,
)

data class AlbumSummary(
    val albumId: Long,
    val album: String,
    val artist: String,
    val trackCount: Int,
    val totalDurationMs: Long,
    val sampleTrackId: Long,
    val sampleContentUri: String,
)

data class ArtistSummary(
    val artist: String,
    val trackCount: Int,
    val albumCount: Int,
)

data class GenreSummary(
    val genre: String,
    val trackCount: Int,
)

data class TrackStatsSnapshot(
    val mediaStoreId: Long,
    val playCount: Int,
    val lastPlayedAtMs: Long?,
    val bitrateBps: Int,
    val sampleRateHz: Int,
)
