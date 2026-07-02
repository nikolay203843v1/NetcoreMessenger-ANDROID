package com.netcoremessenger.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.netcoremessenger.core.data.local.converter.Converters
import com.netcoremessenger.core.data.local.dao.ChatDao
import com.netcoremessenger.core.data.local.dao.MediaDao
import com.netcoremessenger.core.data.local.dao.MessageDao
import com.netcoremessenger.core.data.local.dao.SyncStateDao
import com.netcoremessenger.core.data.local.dao.UserDao
import com.netcoremessenger.core.data.local.entity.ChatEntity
import com.netcoremessenger.core.data.local.entity.ChatParticipantEntity
import com.netcoremessenger.core.data.local.entity.MediaEntity
import com.netcoremessenger.core.data.local.entity.MessageEntity
import com.netcoremessenger.core.data.local.entity.MessageStatusEntity
import com.netcoremessenger.core.data.local.entity.SyncStateEntity
import com.netcoremessenger.core.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        ChatEntity::class,
        ChatParticipantEntity::class,
        MessageEntity::class,
        MessageStatusEntity::class,
        MediaEntity::class,
        SyncStateEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class NetcoreDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun mediaDao(): MediaDao
    abstract fun syncStateDao(): SyncStateDao
}
