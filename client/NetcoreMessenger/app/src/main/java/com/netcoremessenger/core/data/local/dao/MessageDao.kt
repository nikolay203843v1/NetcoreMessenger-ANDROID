package com.netcoremessenger.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.netcoremessenger.core.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("""
        SELECT * FROM messages
        WHERE chat_id = :chatId AND is_deleted = 0
        ORDER BY sort_key DESC
        LIMIT :limit OFFSET :offset
    """)
    fun getMessages(chatId: Long, limit: Int = 50, offset: Int = 0): Flow<List<MessageEntity>>

    @Query("""
        SELECT * FROM messages
        WHERE chat_id = :chatId AND is_deleted = 0
        ORDER BY sort_key DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getMessagesOnce(chatId: Long, limit: Int = 50, offset: Int = 0): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getById(messageId: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE client_id = :clientId")
    suspend fun getByClientId(clientId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Transaction
    suspend fun insertPreservingStatus(message: MessageEntity) {
        val existing = message.id?.let { getById(it) }
        val status = if (existing != null) {
            strongestStatus(existing.status, message.status)
        } else {
            message.status
        }
        insert(message.copy(status = status))
    }

    @Transaction
    suspend fun insertAll(messages: List<MessageEntity>) {
        messages.forEach { insertPreservingStatus(it) }
    }

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatusRaw(messageId: Long, status: String)

    @Query("UPDATE messages SET status = :status WHERE id IN (:messageIds)")
    suspend fun updateStatusesRaw(messageIds: List<Long>, status: String)

    @Transaction
    suspend fun updateStatus(messageId: Long, status: String) {
        val existing = getById(messageId) ?: return
        val next = strongestStatus(existing.status, status)
        if (next != existing.status) {
            updateStatusRaw(messageId, next)
        }
    }

    @Transaction
    suspend fun updateStatuses(messageIds: List<Long>, status: String) {
        if (messageIds.isEmpty()) return
        updateStatusesRaw(messageIds, status)
    }

    @Query("UPDATE messages SET status = :status WHERE client_id = :clientId")
    suspend fun updateStatusByClientId(clientId: String, status: String)

    @Query("UPDATE messages SET content = :content WHERE client_id = :clientId")
    suspend fun updateContentByClientId(clientId: String, content: String)

    @Query("UPDATE messages SET content = :content, status = :status WHERE client_id = :clientId")
    suspend fun updateContentAndStatusByClientId(clientId: String, content: String, status: String)

    @Query("UPDATE messages SET content = :content WHERE id = :messageId")
    suspend fun updateContentById(messageId: Long, content: String)

    @Query("DELETE FROM messages WHERE client_id = :clientId AND status IN ('pending', 'uploading', 'failed')")
    suspend fun deletePendingByClientId(clientId: String)

    @Query("DELETE FROM messages WHERE album_id = :albumId AND status IN ('pending', 'uploading', 'failed')")
    suspend fun deletePendingByAlbumId(albumId: String)

    @Query("UPDATE messages SET status = 'failed' WHERE album_id = :albumId AND status IN ('pending', 'uploading')")
    suspend fun markAlbumFailed(albumId: String)

    @Transaction
    suspend fun confirmSent(clientId: String, serverId: Long, sortKey: Long) {
        val existing = getById(serverId)
        if (existing != null) {
            if (existing.clientId == clientId) {
                // Local auto-generated ID perfectly matched the server ID!
                // We'll proceed to update its status.
            } else if (existing.status != "pending") {
                // The message was already saved via message.new or sync.
                deletePendingByClientId(clientId)
                return
            } else {
                // ID Collision: another pending message took this serverId locally.
                // Let's free up the ID by giving the other pending message a new one.
                deletePendingByClientId(existing.clientId!!)
                insert(existing.copy(id = null))
            }
        }
        val pending = getByClientId(clientId) ?: return
        // Внутри одной @Transaction Room эмитит Flow ОДИН раз — без "мигания пустоты".
        deletePendingByClientId(clientId)
        val confirmed = pending.copy(
            id = serverId,
            sortKey = sortKey,
            status = "sent"
        )
        insert(confirmed)
    }

    @Query("UPDATE messages SET is_deleted = 1 WHERE id = :messageId")
    suspend fun markDeleted(messageId: Long)

    @Query("SELECT * FROM messages WHERE status = 'pending' AND retry_count < 10 AND next_retry_at <= :now ORDER BY sort_key ASC")
    suspend fun getPendingMessages(now: Long = System.currentTimeMillis()): List<MessageEntity>

    @Query("UPDATE messages SET retry_count = retry_count + 1, next_retry_at = :nextRetryAt WHERE client_id = :clientId")
    suspend fun incrementRetry(clientId: String, nextRetryAt: Long)

    @Query("SELECT MAX(sort_key) FROM messages WHERE chat_id = :chatId")
    suspend fun getMaxSortKey(chatId: Long): Long?

    @Query("SELECT MIN(sort_key) FROM messages WHERE chat_id = :chatId")
    suspend fun getMinSortKey(chatId: Long): Long?

    private fun strongestStatus(current: String, incoming: String): String {
        return if (statusRank(incoming) >= statusRank(current)) incoming else current
    }

    private fun statusRank(status: String): Int = when (status) {
        "failed" -> -2
        "pending" -> -1
        "sent" -> 0
        "delivered" -> 1
        "read" -> 2
        else -> 0
    }
}
