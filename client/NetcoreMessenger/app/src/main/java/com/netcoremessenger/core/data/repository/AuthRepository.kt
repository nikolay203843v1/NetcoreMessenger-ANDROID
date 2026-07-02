package com.netcoremessenger.core.data.repository

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import com.netcoremessenger.core.data.remote.api.AuthApi
import com.netcoremessenger.core.data.remote.api.MediaApi
import com.netcoremessenger.core.data.remote.api.UsersApi
import com.netcoremessenger.core.data.remote.dto.AddProfilePhotoRequest
import com.netcoremessenger.core.data.remote.dto.GoogleLoginRequest
import com.netcoremessenger.core.data.remote.dto.MediaDto
import com.netcoremessenger.core.data.remote.dto.ProfilePhotoDto
import com.netcoremessenger.core.data.remote.dto.PushTokenRequest
import com.netcoremessenger.core.data.remote.dto.UpdateProfileRequest
import com.netcoremessenger.core.data.remote.dto.UserDto
import com.netcoremessenger.core.data.remote.interceptor.AuthTokenRefresher
import com.netcoremessenger.core.data.store.TokenDataStore
import com.netcoremessenger.core.data.websocket.WebSocketManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import android.net.Uri
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import com.netcoremessenger.core.data.local.NetcoreDatabase
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val usersApi: UsersApi,
    private val mediaApi: MediaApi,
    private val tokenRefresher: AuthTokenRefresher,
    private val tokenDataStore: TokenDataStore,
    private val webSocketManager: WebSocketManager,
    private val database: NetcoreDatabase,
    @ApplicationContext private val context: Context
) {
    val isAuthenticated: Flow<Boolean> = tokenDataStore.accessToken.map { it != null }

    suspend fun restoreSession(): Boolean {
        val refreshToken = tokenDataStore.refreshToken.first()
        if (!refreshToken.isNullOrBlank()) {
            return tokenRefresher.refreshAccessToken() != null
        }
        return !tokenDataStore.accessToken.first().isNullOrBlank()
    }

    suspend fun googleLogin(idToken: String): Result<Boolean> = runCatching {
        val deviceId = UUID.randomUUID().toString()
        val response = authApi.googleLogin(
            GoogleLoginRequest(
                idToken = idToken,
                deviceId = deviceId,
                platform = "android",
                pushToken = null
            )
        )
        tokenDataStore.saveTokens(response.accessToken, response.refreshToken)
        tokenDataStore.saveUserId(response.userId)
        tokenDataStore.saveDeviceId(deviceId)

        webSocketManager.connect(response.accessToken, deviceId)

        false
    }

    suspend fun updateProfile(
        displayName: String?,
        username: String?,
        bio: String?,
        avatarMediaId: Long? = null
    ): Result<Unit> = runCatching {
        val userDto = usersApi.updateMe(
            UpdateProfileRequest(
                displayName = displayName,
                username = username,
                bio = bio,
                avatarMediaId = avatarMediaId
            )
        )
        val userEntity = com.netcoremessenger.core.data.local.entity.UserEntity(
            id = userDto.id,
            phone = userDto.phone ?: "",
            username = userDto.username,
            displayName = userDto.displayName ?: "User",
            avatarMediaId = userDto.avatarMediaId,
            bio = userDto.bio,
            status = userDto.status ?: "offline",
            lastOnlineAt = userDto.lastOnlineAt ?: 0L
        )
        database.userDao().insert(userEntity)
    }

    suspend fun checkUsername(username: String): Result<Boolean> = runCatching {
        usersApi.checkUsername(username).available
    }

    suspend fun getMe(): Result<UserDto> = runCatching {
        val userDto = usersApi.getMe()
        val userEntity = com.netcoremessenger.core.data.local.entity.UserEntity(
            id = userDto.id,
            phone = userDto.phone ?: "",
            username = userDto.username,
            displayName = userDto.displayName ?: "User",
            avatarMediaId = userDto.avatarMediaId,
            bio = userDto.bio,
            status = userDto.status ?: "offline",
            lastOnlineAt = userDto.lastOnlineAt ?: 0L
        )
        database.userDao().insert(userEntity)
        userDto
    }.onFailure { e ->
        if ((e as? HttpException)?.code() == 401) {
            tokenDataStore.clearTokens()
        }
    }

    suspend fun getMyProfilePhotos(): Result<List<ProfilePhotoDto>> = runCatching {
        usersApi.getMyProfilePhotos()
    }

    suspend fun addProfilePhoto(mediaId: Long): Result<ProfilePhotoDto> = runCatching {
        usersApi.addMyProfilePhoto(AddProfilePhotoRequest(mediaId))
    }

    suspend fun deleteProfilePhoto(photoId: Long): Result<List<ProfilePhotoDto>> = runCatching {
        usersApi.deleteMyProfilePhoto(photoId)
    }

    suspend fun getCachedMe(): com.netcoremessenger.core.data.local.entity.UserEntity? {
        val uid = tokenDataStore.userId.first() ?: return null
        return database.userDao().getById(uid)
    }

    suspend fun uploadAvatar(uri: Uri): Result<MediaDto> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "image/jpeg"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Cannot open image")
            val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", "avatar.jpg", body)
            mediaApi.upload(part)
        }
    }

    suspend fun logout() {
        webSocketManager.disconnect()
        tokenDataStore.clearAll()
        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
    }

    suspend fun connectWebSocket() {
        val refreshToken = tokenDataStore.refreshToken.first()
        val token = if (!refreshToken.isNullOrBlank()) {
            tokenRefresher.refreshAccessToken()
        } else {
            tokenDataStore.accessToken.first()
        }
        val deviceId = tokenDataStore.deviceId.first()
        if (token != null && deviceId != null) {
            webSocketManager.connect(token, deviceId)
            syncPushToken()
        }
    }

    suspend fun syncPushToken() {
        val token = tokenDataStore.accessToken.first() ?: return
        if (token.isBlank()) return
        val pushToken = currentPushToken() ?: return
        runCatching {
            authApi.updatePushToken(PushTokenRequest(pushToken))
        }
    }

    private suspend fun currentPushToken(): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(2_500) {
            runCatching { Tasks.await(FirebaseMessaging.getInstance().token) }.getOrNull()
        }
    }
}
