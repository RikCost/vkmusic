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

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE tracks SET position = position + 1")
    suspend fun shiftPositions()

    /** Новый трек «моей музыки» встаёт первым, как у VK. */
    @Transaction
    suspend fun insertFirst(track: TrackEntity) {
        delete(track.id)
        shiftPositions()
        upsertAll(listOf(track.copy(position = 0)))
    }

    @Transaction
    suspend fun replaceAll(tracks: List<TrackEntity>) {
        clear()
        upsertAll(tracks)
    }
}
