package com.netcoremessenger.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val user_id: Long,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "last_sort_key") val lastSortKey: Long,
    @ColumnInfo(name = "last_sync_at") val lastSyncAt: Long
)
