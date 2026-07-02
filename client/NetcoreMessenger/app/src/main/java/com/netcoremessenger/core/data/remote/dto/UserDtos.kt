package com.netcoremessenger.core.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UserDto(
    @SerializedName("id") val id: Long,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("google_id") val googleId: String? = null,
    @SerializedName("username") val username: String?,
    @SerializedName("display_name") val displayName: String?,
    @SerializedName("bio") val bio: String?,
    @SerializedName("avatar_media_id") val avatarMediaId: Long?,
    @SerializedName("status") val status: String?,
    @SerializedName("last_online_at") val lastOnlineAt: Long?
)

data class UpdateProfileRequest(
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("avatar_media_id") val avatarMediaId: Long? = null
)

data class AddProfilePhotoRequest(
    @SerializedName("media_id") val mediaId: Long
)

data class ProfilePhotoDto(
    @SerializedName("id") val id: Long,
    @SerializedName("media_id") val mediaId: Long,
    @SerializedName("position") val position: Int,
    @SerializedName("created_at") val createdAt: Long?
)

data class UsernameCheckResponse(
    @SerializedName("available") val available: Boolean
)
