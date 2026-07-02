package com.netcoremessenger.feature.chat.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.netcoremessenger.core.util.Constants
import androidx.compose.material3.MaterialTheme
import com.netcoremessenger.ui.anim.bouncingClickable

@Composable
fun VoiceMessagePlayer(content: String, isOutgoing: Boolean) {
    val onBubbleColor = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    
    val player = remember(content) {
        val uriString = if (content.toLongOrNull() != null) {
            "${Constants.BASE_URL}/api/v1/media/$content"
        } else {
            content
        }
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uriString))
            prepare()
        }
    }
    
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .bouncingClickable {
                    if (isPlaying) {
                        player.pause()
                    } else {
                        player.seekTo(0)
                        player.play()
                    }
                    isPlaying = !isPlaying
                }
                .clip(CircleShape)
                .background(if (isOutgoing) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Stop" else "Play",
                tint = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        AudioWaveform(isPlaying = isPlaying, tint = onBubbleColor)
    }
}

@Composable
fun AudioWaveform(isPlaying: Boolean, tint: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barCount = 14
        val baseHeights = remember { listOf(8, 14, 18, 12, 16, 22, 14, 10, 18, 14, 24, 16, 12, 8) }
        val infiniteTransition = rememberInfiniteTransition(label = "waveform")
        
        for (i in 0 until barCount) {
            val animHeight by if (isPlaying) {
                infiniteTransition.animateFloat(
                    initialValue = baseHeights[i % baseHeights.size].toFloat() * 0.4f,
                    targetValue = baseHeights[i % baseHeights.size].toFloat() * 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 350 + (i * 60) % 250, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bar_$i"
                )
            } else {
                remember(isPlaying) { mutableStateOf(baseHeights[i % baseHeights.size].toFloat()) }
            }
            
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = animHeight.dp)
                    .background(tint.copy(alpha = if (isPlaying) 1f else 0.5f), RoundedCornerShape(1.5.dp))
            )
        }
    }
}

