package com.netcoremessenger.core.data.remote.api

import com.netcoremessenger.core.data.remote.dto.MediaDto
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface MediaApi {

    @Multipart
    @POST("api/v1/media/upload")
    suspend fun upload(
        @Part file: MultipartBody.Part
    ): MediaDto
}
