package com.netcoremessenger.feature.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.netcoremessenger.core.util.Constants
import com.netcoremessenger.ui.theme.GradientPrimaryStart
import com.netcoremessenger.ui.theme.GradientPrimaryEnd

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageToolbar(
    title: String,
    isOnline: Boolean,
    avatarMediaId: Long?,
    onNavigateBack: () -> Unit,
    onTitleTap: () -> Unit = {},
    onAudioCall: () -> Unit = {},
    onVideoCall: () -> Unit = {},
    isTyping: Boolean = false
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onTitleTap)
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(listOf(GradientPrimaryStart, GradientPrimaryEnd)),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarMediaId != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data("${Constants.BASE_URL}/api/v1/media/$avatarMediaId")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    val (subtitle, subColor) = when {
                        isTyping -> "печатает..." to MaterialTheme.colorScheme.primary
                        isOnline -> "в сети" to MaterialTheme.colorScheme.primary
                        else -> "не в сети" to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subColor
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        actions = {
            IconButton(onClick = onAudioCall) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = "Audio call",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(onClick = onVideoCall) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Video call",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        )
    )
}
