package com.netcoremessenger.core.data.remote.api

import com.netcoremessenger.core.data.remote.dto.AddProfilePhotoRequest
import com.netcoremessenger.core.data.remote.dto.ProfilePhotoDto
import com.netcoremessenger.core.data.remote.dto.UpdateProfileRequest
import com.netcoremessenger.core.data.remote.dto.UserDto
import com.netcoremessenger.core.data.remote.dto.UsernameCheckResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

interface UsersApi {

    @GET("api/v1/users/me")
    suspend fun getMe(): UserDto

    @PATCH("api/v1/users/me")
    suspend fun updateMe(@Body request: UpdateProfileRequest): UserDto

    @GET("api/v1/users/me/photos")
    suspend fun getMyProfilePhotos(): List<ProfilePhotoDto>

    @POST("api/v1/users/me/photos")
    suspend fun addMyProfilePhoto(@Body request: AddProfilePhotoRequest): ProfilePhotoDto

    @DELETE("api/v1/users/me/photos/{photoId}")
    suspend fun deleteMyProfilePhoto(@Path("photoId") photoId: Long): List<ProfilePhotoDto>

    @GET("api/v1/users/{userId}/photos")
    suspend fun getUserProfilePhotos(@Path("userId") userId: Long): List<ProfilePhotoDto>

    @GET("api/v1/users/check-username/{username}")
    suspend fun checkUsername(@Path("username") username: String): UsernameCheckResponse

    @GET("api/v1/users/{userId}")
    suspend fun getUser(@Path("userId") userId: Long): UserDto

    @GET("api/v1/users/search")
    suspend fun searchUsers(@Query("q") query: String): List<UserDto>
}
