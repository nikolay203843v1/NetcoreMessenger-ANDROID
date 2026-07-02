package com.netcoremessenger.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.netcoremessenger.core.data.local.entity.MediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Query("SELECT * FROM media WHERE id = :mediaId")
    suspend fun getById(mediaId: Long): MediaEntity?

    @Query("SELECT * FROM media WHERE id = :mediaId")
    fun observeById(mediaId: Long): Flow<MediaEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: MediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(media: List<MediaEntity>)

    @Query("UPDATE media SET is_ready = 1, storage_path = :storagePath WHERE id = :mediaId")
    suspend fun markReady(mediaId: Long, storagePath: String?)

    @Query("UPDATE media SET thumbnail_media_id = :thumbnailId WHERE id = :mediaId")
    suspend fun updateThumbnail(mediaId: Long, thumbnailId: Long)

    @Query("DELETE FROM media WHERE id = :mediaId")
    suspend fun delete(mediaId: Long)
}
