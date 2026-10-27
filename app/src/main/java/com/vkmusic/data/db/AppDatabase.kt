package com.vkmusic.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TrackEntity::class, ExportedTrackEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    abstract fun exportedTrackDao(): ExportedTrackDao
}
