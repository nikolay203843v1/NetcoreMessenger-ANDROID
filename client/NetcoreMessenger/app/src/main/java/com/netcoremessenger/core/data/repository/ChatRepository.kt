package com.netcoremessenger.core.data.repository

import com.netcoremessenger.core.data.local.dao.ChatDao
import com.netcoremessenger.core.data.local.entity.ChatEntity
import com.netcoremessenger.core.data.local.dao.UserDao
import com.netcoremessenger.core.data.local.entity.UserEntity
import com.netcoremessenger.core.data.local.entity.ChatParticipantEntity
import com.netcoremessenger.core.data.remote.api.ChatsApi
import com.netcoremessenger.core.data.remote.api.UsersApi
import com.netcoremessenger.core.data.remote.dto.CreateChatRequest
import com.netcoremessenger.core.data.remote.dto.UpdateChatRequest
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val userDao: UserDao,
    private val chatsApi: ChatsApi,
    private val usersApi: UsersApi
) {
    fun observeChats(currentUserId: Long): Flow<List<com.netcoremessenger.core.data.local.dao.ChatWithLastMessage>> {
        return chatDao.getChatsWithLastMessage(currentUserId)
    }

    fun observeChatPartner(chatId: Long, currentUserId: Long): Flow<UserEntity?> {
        return userDao.observeChatPartner(chatId, currentUserId)
    }

    suspend fun fetchChats(currentUserId: Long): Result<Unit> = runCatching {
        val remoteChats = chatsApi.getChats()
        
        // 1. Insert participant user entities first so that FK checks pass
        val usersToInsert = mutableListOf<UserEntity>()
        for (chatDto in remoteChats) {
            val participants = chatDto.participants ?: emptyList()
            for (part in participants) {
                var userDto = part.user
                if (userDto == null || userDto.displayName.isNullOrBlank() || userDto.displayName == "Unknown") {
                    runCatching { usersApi.getUser(part.userId) }.onSuccess { fetchedUser ->
                        userDto = fetchedUser
                    }
                }
                val finalUserDto = userDto
                usersToInsert.add(
                    UserEntity(
                        id = finalUserDto?.id ?: part.userId,
                        phone = finalUserDto?.phone ?: "",
                        username = finalUserDto?.username,
                        displayName = if (!finalUserDto?.displayName.isNullOrBlank()) finalUserDto.displayName else "User ${part.userId}",
                        avatarMediaId = finalUserDto?.avatarMediaId,
                        bio = finalUserDto?.bio,
                        status = finalUserDto?.status ?: "offline",
                        lastOnlineAt = finalUserDto?.lastOnlineAt ?: 0L
                    )
                )
            }
        }
        userDao.insertAll(usersToInsert.distinctBy { it.id })

        // 2. Insert chat entities
        val entities = remoteChats.map { dto ->
            ChatEntity(
                id = dto.id,
                type = dto.type,
                title = dto.title,
                description = dto.description,
                avatarMediaId = dto.avatarMediaId,
                creatorId = dto.creatorId,
                inviteLink = dto.inviteLink,
                isDeleted = false
            )
        }
        chatDao.insertAll(entities)

        // 3. Insert chat participants
        val participantEntities = remoteChats.flatMap { chatDto ->
            chatDto.participants?.map { part ->
                ChatParticipantEntity(
                    chatId = chatDto.id,
                    userId = part.userId,
                    role = part.role,
                    joinedAt = part.joinedAt
                )
            } ?: emptyList()
        }
        chatDao.insertParticipants(participantEntities)
    }

    suspend fun openPrivateChat(otherUserId: Long): Result<Long> = createChat(listOf(otherUserId))

    suspend fun createChat(participantIds: List<Long>, title: String? = null): Result<Long> = runCatching {
        val response = chatsApi.createChat(
            CreateChatRequest(
                type = if (participantIds.size == 1) "private" else "group",
                title = title,
                participantIds = participantIds
            )
        )
        fetchChatDetails(response.id).getOrThrow()
        response.id
    }

    suspend fun getChat(chatId: Long): Result<ChatEntity?> = runCatching {
        chatDao.getById(chatId)
    }

    suspend fun fetchChatDetails(chatId: Long): Result<Unit> = runCatching {
        val chatDto = chatsApi.getChat(chatId)
        
        // 1. Insert participant user entities
        val participants = chatDto.participants ?: emptyList()
        val usersToInsert = mutableListOf<UserEntity>()
        for (part in participants) {
            var userDto = part.user
            if (userDto == null || userDto.displayName.isNullOrBlank() || userDto.displayName == "Unknown") {
                runCatching { usersApi.getUser(part.userId) }.onSuccess { fetchedUser ->
                    userDto = fetchedUser
                }
            }
            val finalUserDto = userDto
            usersToInsert.add(
                UserEntity(
                    id = finalUserDto?.id ?: part.userId,
                    phone = finalUserDto?.phone ?: "",
                    username = finalUserDto?.username,
                    displayName = if (!finalUserDto?.displayName.isNullOrBlank()) finalUserDto.displayName else "User ${part.userId}",
                    avatarMediaId = finalUserDto?.avatarMediaId,
                    bio = finalUserDto?.bio,
                    status = finalUserDto?.status ?: "offline",
                    lastOnlineAt = finalUserDto?.lastOnlineAt ?: 0L
                )
            )
        }
        userDao.insertAll(usersToInsert.distinctBy { it.id })

        // 2. Insert chat entity
        val entity = ChatEntity(
            id = chatDto.id,
            type = chatDto.type,
            title = chatDto.title,
            description = chatDto.description,
            avatarMediaId = chatDto.avatarMediaId,
            creatorId = chatDto.creatorId,
            inviteLink = chatDto.inviteLink,
            isDeleted = false
        )
        chatDao.insert(entity)

        // 3. Insert participants
        val participantEntities = chatDto.participants?.map { part ->
            ChatParticipantEntity(
                chatId = chatDto.id,
                userId = part.userId,
                role = part.role,
                joinedAt = part.joinedAt
            )
        } ?: emptyList()
        chatDao.insertParticipants(participantEntities)
    }

    suspend fun getChatPartner(chatId: Long, currentUserId: Long): Result<UserEntity?> = runCatching {
        val initialPartner = userDao.getChatPartner(chatId, currentUserId)
        if (initialPartner == null || initialPartner.displayName == "Unknown" || initialPartner.displayName?.startsWith("User ") == true) {
            fetchChatDetails(chatId).getOrNull()
            userDao.getChatPartner(chatId, currentUserId)
        } else {
            initialPartner
        }
    }

    suspend fun deleteChat(chatId: Long): Result<Unit> = runCatching {
        chatsApi.deleteChat(chatId)
        chatDao.markDeleted(chatId)
    }

    suspend fun updateGroup(
        chatId: Long,
        title: String?,
        description: String? = null,
        avatarMediaId: Long? = null
    ): Result<Unit> = runCatching {
        chatsApi.updateChat(
            chatId,
            UpdateChatRequest(
                title = title,
                description = description,
                avatarMediaId = avatarMediaId
            )
        )
        fetchChatDetails(chatId).getOrThrow()
    }

    suspend fun addParticipant(chatId: Long, userId: Long): Result<Unit> = runCatching {
        chatsApi.addParticipant(chatId, userId)
        fetchChatDetails(chatId).getOrThrow()
    }

    suspend fun removeParticipant(chatId: Long, userId: Long): Result<Unit> = runCatching {
        chatsApi.removeParticipant(chatId, userId)
        fetchChatDetails(chatId).getOrThrow()
    }

    suspend fun markChatDeleted(chatId: Long) {
        chatDao.markDeleted(chatId)
    }
}
