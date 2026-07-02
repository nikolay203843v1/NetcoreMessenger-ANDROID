package com.netcoremessenger.core.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ChatDto(
    @SerializedName("id") val id: Long,
    @SerializedName("type") val type: String,
    @SerializedName("title") val title: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("avatar_media_id") val avatarMediaId: Long?,
    @SerializedName("creator_id") val creatorId: Long?,
    @SerializedName("invite_link") val inviteLink: String?,
    @SerializedName("participants") val participants: List<ParticipantDto>?,
    @SerializedName("created_at") val createdAt: Long
)

data class ParticipantDto(
    @SerializedName("user_id") val userId: Long,
    @SerializedName("role") val role: String,
    @SerializedName("joined_at") val joinedAt: Long,
    @SerializedName("user") val user: UserDto?
)

data class CreateChatRequest(
    @SerializedName("type") val type: String,
    @SerializedName("title") val title: String? = null,
    @SerializedName("participant_ids") val participantIds: List<Long>
)

data class UpdateChatRequest(
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("avatar_media_id") val avatarMediaId: Long? = null
)
