package com.netcoremessenger.core.data.websocket

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class WebSocketEnvelope(
    @SerializedName("event") val event: String,
    @SerializedName("data") val data: JsonElement?,
    @SerializedName("id") val id: String? = null,
    @SerializedName("timestamp") val timestamp: Long? = null
)
