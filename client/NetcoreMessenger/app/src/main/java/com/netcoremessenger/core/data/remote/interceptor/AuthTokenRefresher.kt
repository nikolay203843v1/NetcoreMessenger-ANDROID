package com.netcoremessenger.core.data.remote.interceptor

import com.google.gson.Gson
import com.netcoremessenger.core.data.store.TokenDataStore
import com.netcoremessenger.core.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthTokenRefresher @Inject constructor(
    private val tokenDataStore: TokenDataStore,
    private val gson: Gson
) {
    private val client = OkHttpClient.Builder().build()
    private val mutex = Mutex()

    suspend fun refreshAccessToken(): String? = mutex.withLock {
        val refreshToken = tokenDataStore.refreshToken.first() ?: return@withLock null

        withContext(Dispatchers.IO) {
            runCatching {
                val body = gson.toJson(mapOf("refresh_token" to refreshToken))
                val request = Request.Builder()
                    .url("${Constants.BASE_URL}/api/v1/auth/refresh")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == 400 || response.code == 401 || response.code == 403) {
                            tokenDataStore.clearTokens()
                        }
                        return@withContext null
                    }

                    val responseBody = response.body?.string() ?: return@withContext null
                    val tokens = gson.fromJson(responseBody, TokenRefreshResponse::class.java)
                    tokenDataStore.saveTokens(tokens.access_token, tokens.refresh_token)
                    tokens.access_token
                }
            }.getOrNull()
        }
    }
}

private data class TokenRefreshResponse(
    val access_token: String,
    val refresh_token: String
)
