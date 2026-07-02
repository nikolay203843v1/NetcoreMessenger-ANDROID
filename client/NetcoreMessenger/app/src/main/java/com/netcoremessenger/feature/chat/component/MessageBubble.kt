package com.netcoremessenger.feature.chat.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.netcoremessenger.feature.chat.MessageUiModel
import com.netcoremessenger.core.util.DateUtils
import java.time.Instant

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageUiModel.Message,
    isHighlighted: Boolean = false,
    circleUploadProgress: Int? = null,
    onLongPress: (MessageUiModel.Message) -> Unit,
    onMediaTap: (Long) -> Unit,
    onReplyTap: (MessageUiModel.Message) -> Unit,
    onReplyPreviewTap: (MessageUiModel.Message) -> Unit = {},
    onCancelCircleUpload: (MessageUiModel.Message) -> Unit = {},
    isCirclePlaybackActive: Boolean = true
) {
    val alignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    val isCircleMessage = message.type == "circle"
    val onBubbleColor = if (message.isOutgoing && !isCircleMessage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    var dragTotal by remember(message.localId) { mutableFloatStateOf(0f) }

    val bubbleShape = if (message.isOutgoing) {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 4.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 4.dp,
            bottomEnd = 16.dp
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(message.localId) {
                detectHorizontalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        dragTotal += dragAmount
                    },
                    onDragEnd = {
                        val shouldReply = if (message.isOutgoing) {
                            dragTotal > 72f
                        } else {
                            dragTotal < -72f
                        }
                        if (shouldReply) {
                            onReplyTap(message)
                        }
                        dragTotal = 0f
                    },
                    onDragCancel = { dragTotal = 0f }
                )
            }
            .combinedClickable(
                onClick = { onLongPress(message) },
                onLongClick = { onLongPress(message) }
            )
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .then(if (isCircleMessage) Modifier.fillMaxWidth() else Modifier.widthIn(max = 280.dp))
                .then(if (isCircleMessage) Modifier else Modifier.clip(bubbleShape))
                .then(
                    if (isCircleMessage) {
                        Modifier
                    } else if (message.isOutgoing) {
                        Modifier.background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
                                )
                            )
                        )
                    } else {
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = bubbleShape
                            )
                    }
                )
                .then(
                    if (isHighlighted) {
                        Modifier.border(
                            width = 2.dp,
                            color = Color.White.copy(alpha = 0.75f),
                            shape = bubbleShape
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(
                    horizontal = if (isCircleMessage) 0.dp else 14.dp,
                    vertical = if (isCircleMessage) 0.dp else 10.dp
                )
        ) {
            Column {
                (message.replyTo as? MessageUiModel.Message)?.let { reply ->
                    InlineReplyPreview(
                        reply = reply,
                        isOutgoing = message.isOutgoing,
                        onClick = { onReplyPreviewTap(reply) }
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                }

                when (message.type) {
                    "text" -> {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = onBubbleColor
                        )
                    }
                    "image" -> {
                        val mid = message.content.toLongOrNull() ?: 0L
                        val url = if (mid == 0L) message.content else "${com.netcoremessenger.core.util.Constants.BASE_URL}/api/v1/media/$mid/thumbnail"
                        ImageMessageView(
                            mediaId = mid,
                            thumbnailUrl = url,
                            caption = null,
                            isOutgoing = message.isOutgoing,
                            isPending = message.status == "pending",
                            onLongPress = { onLongPress(message) },
                            onTap = { onMediaTap(mid) }
                        )
                    }
                    "video" -> {
                        val mid = message.content.toLongOrNull() ?: 0L
                        val url = if (mid == 0L) message.content else "${com.netcoremessenger.core.util.Constants.BASE_URL}/api/v1/media/$mid"
                        VideoMessageView(
                            mediaId = mid,
                            thumbnailUrl = url,
                            durationMs = 0,
                            caption = null,
                            isOutgoing = message.isOutgoing,
                            onTap = { onMediaTap(mid) }
                        )
                    }
                    "voice" -> {
                        VoiceMessagePlayer(content = message.content, isOutgoing = message.isOutgoing)
                    }
                    "circle" -> {
                        val mid = message.content.toLongOrNull() ?: 0L
                        val source: Any? = if (mid == 0L) {
                            java.io.File(message.content).takeIf { it.exists() }
                        } else {
                            "${com.netcoremessenger.core.util.Constants.BASE_URL}/api/v1/media/$mid"
                        }
                        CircleMessageView(
                            mediaId = mid,
                            thumbnailUrl = source,
                            isOutgoing = message.isOutgoing,
                            isPlaybackActive = isCirclePlaybackActive,
                            uploadProgress = circleUploadProgress.takeIf { message.status == "uploading" },
                            onCancelUpload = { onCancelCircleUpload(message) },
                            onTap = { if (mid > 0L) onMediaTap(mid) }
                        )
                    }
                    "location" -> {
                        LocationMessageView(
                            address = message.content,
                            isOutgoing = message.isOutgoing,
                            onTap = { /* TODO */ }
                        )
                    }
                    "contact" -> {
                        ContactMessageView(
                            name = message.content,
                            isOutgoing = message.isOutgoing,
                            onMessageTap = { /* TODO */ }
                        )
                    }
                    else -> {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = onBubbleColor
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = DateUtils.formatTime(message.timestamp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = if (message.isOutgoing && !isCircleMessage) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    if (message.isOutgoing) {
                        DeliveryCheckmark(status = message.status)
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineReplyPreview(
    reply: MessageUiModel.Message,
    isOutgoing: Boolean,
    onClick: () -> Unit
) {
    val textColor = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val previewText = when (reply.type) {
        "image" -> "Фото"
        "voice" -> "Голосовое сообщение"
        "video" -> "Видео"
        "circle" -> "Кружок"
        else -> reply.content
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isOutgoing) Color.White.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.background.copy(alpha = 0.45f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isOutgoing) Color.White else MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "В ответ",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isOutgoing) Color.White else MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Text(
                text = previewText,
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DeliveryCheckmark(status: String) {
    val activeColor = Color(0xFF00E676) // Bright green
    val inactiveColor = Color.White.copy(alpha = 0.7f)

    if (status == "pending") {
        Icon(
            imageVector = Icons.Default.AccessTime,
            contentDescription = "Pending",
            tint = inactiveColor,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(11.dp)
        )
    } else {
        Row(
            modifier = Modifier.padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = status,
                tint = if (status == "read") activeColor else inactiveColor,
                modifier = Modifier.size(12.dp)
            )
            if (status == "delivered" || status == "read") {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = status,
                    tint = if (status == "read") activeColor else inactiveColor,
                    modifier = Modifier
                        .size(12.dp)
                        .offset(x = (-7).dp)
                )
            }
        }
    }
}

@Composable
fun ServiceMessage(content: String, timestamp: Instant? = null, isOutgoing: Boolean = false) {
    val isCallLog = content.contains("звон", ignoreCase = true)
    val isMissed = content.contains("пропущ", ignoreCase = true)
    val iconColor = when {
        isMissed -> Color(0xFFFF4D5E)
        isCallLog -> if (isOutgoing) Color.White else Color(0xFF35C759)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = if (isMissed) Icons.Default.CallMissed else Icons.Default.CallMade
    val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val textColor = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = alignment
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .then(
                    if (isOutgoing) {
                        Modifier.background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
                                )
                            )
                        )
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    }
                )
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isCallLog) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
            }
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = textColor
            )
            if (timestamp != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = DateUtils.formatTime(timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.68f)
                )
            }
        }
    }
}
