package com.netcoremessenger.core.data.remote.dto

import com.google.gson.annotations.SerializedName

data class MediaDto(
    @SerializedName("id") val id: Long,
    @SerializedName("type") val type: String,
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("size_bytes") val sizeBytes: Long,
    @SerializedName("url") val url: String
)
