package com.netcoremessenger.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.netcoremessenger.core.data.local.entity.SyncStateEntity

@Dao
interface SyncStateDao {

    @Query("SELECT * FROM sync_state WHERE user_id = :userId")
    suspend fun getByUserId(userId: Long): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(syncState: SyncStateEntity)

    @Query("UPDATE sync_state SET last_sort_key = :lastSortKey, last_sync_at = :lastSyncAt WHERE user_id = :userId")
    suspend fun update(userId: Long, lastSortKey: Long, lastSyncAt: Long)
}
