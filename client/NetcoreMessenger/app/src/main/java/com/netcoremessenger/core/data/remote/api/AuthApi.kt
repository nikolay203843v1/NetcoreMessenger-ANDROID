package com.netcoremessenger.core.data.remote.api

import com.netcoremessenger.core.data.remote.dto.GoogleLoginRequest
import com.netcoremessenger.core.data.remote.dto.GoogleLoginResponse
import com.netcoremessenger.core.data.remote.dto.PushTokenRequest
import com.netcoremessenger.core.data.remote.dto.RefreshTokenRequest
import com.netcoremessenger.core.data.remote.dto.RefreshTokenResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("api/v1/auth/google")
    suspend fun googleLogin(@Body request: GoogleLoginRequest): GoogleLoginResponse

    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): RefreshTokenResponse

    @POST("api/v1/auth/push-token")
    suspend fun updatePushToken(@Body request: PushTokenRequest)
}
