package com.netcoremessenger.core.data.remote.dto

import com.google.gson.annotations.SerializedName

data class GoogleLoginRequest(
    @SerializedName("id_token") val idToken: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("platform") val platform: String = "android",
    @SerializedName("push_token") val pushToken: String? = null
)

data class GoogleLoginResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Int,
    @SerializedName("user_id") val userId: Long
)

data class RefreshTokenRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class RefreshTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Int
)

data class PushTokenRequest(
    @SerializedName("push_token") val pushToken: String?
)
