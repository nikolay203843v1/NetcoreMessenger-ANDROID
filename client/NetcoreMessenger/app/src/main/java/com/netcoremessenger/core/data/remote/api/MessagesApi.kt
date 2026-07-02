package com.netcoremessenger.core.data.remote.api

import com.netcoremessenger.core.data.remote.dto.MessageDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MessagesApi {

    @GET("api/v1/messages/{chatId}")
    suspend fun getMessages(
        @Path("chatId") chatId: Long,
        @Query("before_sort_key") before: Long? = null,
        @Query("limit") limit: Int = 50
    ): List<MessageDto>

    @POST("api/v1/messages")
    suspend fun sendMessage(
        @Body request: com.netcoremessenger.core.data.remote.dto.SendMessageRequest
    ): MessageDto

    @DELETE("api/v1/messages/{messageId}")
    suspend fun deleteMessage(@Path("messageId") messageId: Long)

    @PATCH("api/v1/messages/{messageId}")
    suspend fun editMessage(
        @Path("messageId") messageId: Long,
        @Body request: com.netcoremessenger.core.data.remote.dto.EditMessageRequest
    ): MessageDto

    @POST("api/v1/messages/{messageId}/forward")
    suspend fun forwardMessage(
        @Path("messageId") messageId: Long,
        @Body request: com.netcoremessenger.core.data.remote.dto.ForwardMessageRequest
    ): MessageDto
}
