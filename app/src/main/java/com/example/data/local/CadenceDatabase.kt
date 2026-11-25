package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        MediaTrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        FolderFilterEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(CadenceTypeConverters::class)
abstract class CadenceDatabase : RoomDatabase() {
    abstract fun cadenceDao(): CadenceDao

    companion object {
        @Volatile
        private var instance: CadenceDatabase? = null

        fun getInstance(context: Context): CadenceDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CadenceDatabase::class.java,
                    "cadence_media.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
