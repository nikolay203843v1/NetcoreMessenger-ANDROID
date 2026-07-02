package com.netcoremessenger.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Long,
    val phone: String,
    val username: String?,
    @ColumnInfo(name = "display_name") val displayName: String?,
    val bio: String?,
    @ColumnInfo(name = "avatar_media_id") val avatarMediaId: Long?,
    val status: String?,
    @ColumnInfo(name = "last_online_at") val lastOnlineAt: Long?
)
