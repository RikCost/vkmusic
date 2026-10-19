package com.vkmusic.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY position")
    fun observeAll(): Flow<List<TrackEntity>>

    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(tracks: List<TrackEntity>) {
        clear()
        upsertAll(tracks)
    }
}
