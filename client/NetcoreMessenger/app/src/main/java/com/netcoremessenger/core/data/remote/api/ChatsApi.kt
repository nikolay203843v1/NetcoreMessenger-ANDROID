package com.netcoremessenger.core.data.remote.api

import com.netcoremessenger.core.data.remote.dto.ChatDto
import com.netcoremessenger.core.data.remote.dto.CreateChatRequest
import com.netcoremessenger.core.data.remote.dto.UpdateChatRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ChatsApi {

    @GET("api/v1/chats")
    suspend fun getChats(
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 50
    ): List<ChatDto>

    @GET("api/v1/chats/{chatId}")
    suspend fun getChat(@Path("chatId") chatId: Long): ChatDto

    @POST("api/v1/chats")
    suspend fun createChat(@Body request: CreateChatRequest): ChatDto

    @PATCH("api/v1/chats/{chatId}")
    suspend fun updateChat(
        @Path("chatId") chatId: Long,
        @Body request: UpdateChatRequest
    ): ChatDto

    @DELETE("api/v1/chats/{chatId}")
    suspend fun deleteChat(@Path("chatId") chatId: Long)

    @POST("api/v1/chats/{chatId}/participants/{userId}")
    suspend fun addParticipant(
        @Path("chatId") chatId: Long,
        @Path("userId") userId: Long,
        @Query("role") role: String = "member"
    )

    @DELETE("api/v1/chats/{chatId}/participants/{userId}")
    suspend fun removeParticipant(
        @Path("chatId") chatId: Long,
        @Path("userId") userId: Long
    )
}
