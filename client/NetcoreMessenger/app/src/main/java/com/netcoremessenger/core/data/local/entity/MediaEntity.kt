package com.netcoremessenger.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media")
data class MediaEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "user_id") val userId: Long,
    val type: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "thumbnail_media_id") val thumbnailMediaId: Long?,
    @ColumnInfo(name = "storage_path") val storagePath: String?,
    @ColumnInfo(name = "is_ready") val isReady: Boolean = false
)
