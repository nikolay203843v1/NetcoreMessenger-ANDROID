package com.netcoremessenger.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.netcoremessenger.core.data.local.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

data class ChatWithLastMessage(
    val id: Long,
    val type: String,
    val title: String?,
    val avatarMediaId: Long?,
    val lastMessage: String?,
    val lastMessageType: String?,
    val lastImageIds: String?,
    val lastMessageTime: Long?,
    val lastMessageSenderId: Long?,
    val otherUserId: Long?,
    val otherUserName: String?,
    val otherUserAvatar: Long?,
    val otherUserStatus: String?,
    val unreadCount: Int
)

@Dao
interface ChatDao {

    @Query("""
        SELECT c.id, c.type, c.title, c.avatar_media_id as avatarMediaId,
               m.content as lastMessage, m.type as lastMessageType,
               (
                   SELECT GROUP_CONCAT(content, ',') FROM (
                       SELECT mi.content FROM messages mi
                       WHERE mi.chat_id = c.id
                         AND mi.is_deleted = 0
                         AND mi.type = 'image'
                         AND (
                             (m.album_id IS NOT NULL AND mi.album_id = m.album_id)
                             OR (m.album_id IS NULL AND mi.id = m.id)
                         )
                       ORDER BY mi.sort_key ASC
                       LIMIT 4
                   )
               ) as lastImageIds,
               m.created_at as lastMessageTime, m.sender_id as lastMessageSenderId,
               u.id as otherUserId,
               u.display_name as otherUserName, u.avatar_media_id as otherUserAvatar,
               u.status as otherUserStatus,
               (SELECT COUNT(*) FROM messages
                  WHERE chat_id = c.id
                    AND status != 'read'
                    AND is_deleted = 0
                    AND sender_id != :currentUserId) as unreadCount
        FROM chats c
        LEFT JOIN messages m ON m.id = (
            SELECT id FROM messages WHERE chat_id = c.id
            AND is_deleted = 0 ORDER BY sort_key DESC LIMIT 1
        )
        LEFT JOIN users u ON u.id = (
            SELECT user_id FROM chat_participants
            WHERE chat_id = c.id AND user_id != :currentUserId LIMIT 1
        )
        WHERE c.is_deleted = 0
        ORDER BY m.created_at DESC
    """)
    fun getChatsWithLastMessage(currentUserId: Long): Flow<List<ChatWithLastMessage>>

    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getById(chatId: Long): ChatEntity?

    @Query("SELECT * FROM chats WHERE id = :chatId")
    fun observeById(chatId: Long): Flow<ChatEntity?>

    @Upsert
    suspend fun insert(chat: ChatEntity)

    @Upsert
    suspend fun insertAll(chats: List<ChatEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipants(participants: List<com.netcoremessenger.core.data.local.entity.ChatParticipantEntity>)

    @Query("UPDATE chats SET title = :title, description = :description, avatar_media_id = :avatarMediaId WHERE id = :chatId")
    suspend fun update(chatId: Long, title: String?, description: String?, avatarMediaId: Long?)

    @Query("UPDATE chats SET is_deleted = 1 WHERE id = :chatId")
    suspend fun markDeleted(chatId: Long)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun delete(chatId: Long)
}
