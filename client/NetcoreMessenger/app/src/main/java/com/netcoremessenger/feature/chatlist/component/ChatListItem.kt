package com.netcoremessenger.feature.chatlist.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.netcoremessenger.feature.chatlist.ChatListItemUi
import com.netcoremessenger.core.util.DateUtils
import com.netcoremessenger.ui.anim.bouncingClickable
import com.netcoremessenger.ui.theme.OnlineGreen
import com.netcoremessenger.ui.theme.UnreadBadgeColor
import com.netcoremessenger.ui.theme.GradientPrimaryStart
import com.netcoremessenger.ui.theme.GradientPrimaryEnd

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListItem(
    chat: ChatListItemUi,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
                shape = RoundedCornerShape(18.dp)
            )
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OnlineAvatar(
            imageUrl = chat.avatarUrl,
            name = chat.title,
            isOnline = chat.isOnline,
            size = 52.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (chat.lastMessageTime != null) {
                    Text(
                        text = DateUtils.formatDate(chat.lastMessageTime),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (chat.isTyping) {
                    TypingIndicator()
                } else {
                    if (chat.lastImagePreviewUrls.isNotEmpty()) {
                        LastImagePreview(urls = chat.lastImagePreviewUrls)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = chat.lastMessage ?: "Сообщений пока нет",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (chat.unreadCount > 0) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (chat.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    UnreadBadge(count = chat.unreadCount)
                }
            }
        }
    }
}

@Composable
private fun LastImagePreview(urls: List<String>) {
    val visible = urls.take(4)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (visible.size == 1) {
            AsyncImage(
                model = visible.first(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                visible.chunked(2).take(2).forEach { row ->
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        row.forEach { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OnlineAvatar(
    imageUrl: String?,
    name: String,
    isOnline: Boolean,
    size: androidx.compose.ui.unit.Dp = 52.dp,
    showOnlineDot: Boolean = true
) {
    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    if (imageUrl.isNullOrBlank()) {
                        val charCode = if (name.isNotEmpty()) name[0].code else 0
                        val colors = when (charCode % 4) {
                            0 -> listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                            1 -> listOf(Color(0xFFEC4899), Color(0xFFF43F5E))
                            2 -> listOf(Color(0xFF10B981), Color(0xFF059669))
                            else -> listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                        }
                        Brush.linearGradient(colors)
                    } else {
                        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                )
                .background(if (imageUrl.isNullOrBlank()) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                val initials = if (name.isNotEmpty()) name[0].uppercaseChar().toString() else "?"
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }

        if (isOnline && showOnlineDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(OnlineGreen)
            )
        }
    }
}

@Composable
fun UnreadBadge(count: Int) {
    Box(
        modifier = Modifier
            .height(20.dp)
            .width(if (count > 99) 32.dp else 20.dp)
            .clip(CircleShape)
            .background(UnreadBadgeColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 1
        )
    }
}

@Composable
fun TypingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "typing...",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
    }
}
