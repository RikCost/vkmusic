package com.vkmusic.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Трек, сохранённый файлом в папку из настроек. Записывается после успешного экспорта и живёт
 * отдельно от истории WorkManager: файл переживает перезапуск, чистку кэша и удаление трека из VK.
 */
@Entity(tableName = "exported_tracks")
data class ExportedTrackEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val location: String,
    val mimeType: String,
    val sizeBytes: Long,
    val savedAtMs: Long,
)

@Dao
interface ExportedTrackDao {
    @Query("SELECT * FROM exported_tracks ORDER BY savedAtMs DESC")
    fun observeAll(): Flow<List<ExportedTrackEntity>>

    @Query("SELECT * FROM exported_tracks")
    suspend fun all(): List<ExportedTrackEntity>

    @Upsert
    suspend fun upsert(track: ExportedTrackEntity)

    @Query("DELETE FROM exported_tracks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM exported_tracks")
    suspend fun deleteAll()
}
