package com.netcoremessenger.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.netcoremessenger.core.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getById(userId: Long): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId")
    fun observeById(userId: Long): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(users: List<UserEntity>)

    @Query("UPDATE users SET status = :status WHERE id = :userId")
    suspend fun updateStatus(userId: Long, status: String)

    @Query("UPDATE users SET last_online_at = :lastOnlineAt WHERE id = :userId")
    suspend fun updateLastOnline(userId: Long, lastOnlineAt: Long)

    @Query("UPDATE users SET status = :status, last_online_at = :lastOnlineAt WHERE id = :userId")
    suspend fun updatePresence(userId: Long, status: String, lastOnlineAt: Long?)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun delete(userId: Long)

    @Query("SELECT * FROM users WHERE display_name LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<UserEntity>

    @Query("""
        SELECT u.* FROM users u
        INNER JOIN chat_participants cp ON cp.user_id = u.id
        WHERE cp.chat_id = :chatId AND cp.user_id != :currentUserId
        LIMIT 1
    """)
    suspend fun getChatPartner(chatId: Long, currentUserId: Long): UserEntity?

    @Query("""
        SELECT u.* FROM users u
        INNER JOIN chat_participants cp ON cp.user_id = u.id
        WHERE cp.chat_id = :chatId AND cp.user_id != :currentUserId
        LIMIT 1
    """)
    fun observeChatPartner(chatId: Long, currentUserId: Long): Flow<UserEntity?>
}
