package com.netcoremessenger.feature.chat.component

import android.graphics.Matrix
import android.net.Uri
import android.view.TextureView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.netcoremessenger.core.data.store.TokenDataStore
import java.io.File

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun CircleMessageView(
    mediaId: Long,
    thumbnailUrl: Any?,
    isOutgoing: Boolean,
    isPlaybackActive: Boolean = true,
    uploadProgress: Int? = null,
    onCancelUpload: () -> Unit = {},
    onTap: () -> Unit
) {
    val context = LocalContext.current
    val tokenDataStore = remember(context) { TokenDataStore(context.applicationContext) }
    val accessToken by tokenDataStore.accessToken.collectAsState(initial = null)
    val uri = remember(thumbnailUrl) { thumbnailUrl.toVideoUri() }
    val isRemoteUri = uri?.scheme == "http" || uri?.scheme == "https"
    var expanded by remember(mediaId, thumbnailUrl) { mutableStateOf(false) }
    var progress by remember(mediaId, thumbnailUrl) { mutableFloatStateOf(0f) }
    var isPlaying by remember(mediaId, thumbnailUrl) { mutableStateOf(false) }
    var isBuffering by remember(mediaId, thumbnailUrl) { mutableStateOf(false) }
    var videoAspect by remember(mediaId, thumbnailUrl) { mutableFloatStateOf(1f) }
    var playbackFailed by remember(mediaId, thumbnailUrl) { mutableStateOf(false) }

    val player = remember(uri, accessToken, isPlaybackActive, playbackFailed) {
        if (!isPlaybackActive || playbackFailed || uri == null || (isRemoteUri && accessToken.isNullOrBlank())) {
            null
        } else {
            ExoPlayer.Builder(context).build().apply {
                val headers = if (isRemoteUri && !accessToken.isNullOrBlank()) {
                    mapOf("Authorization" to "Bearer $accessToken")
                } else {
                    emptyMap()
                }
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(mapOf("Accept" to "*/*") + headers)
                    .setAllowCrossProtocolRedirects(true)
                val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .build()
                setMediaSource(ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                playWhenReady = true
                prepare()
            }
        }
    }

    DisposableEffect(player) {
        if (player == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_ENDED && expanded) {
                        expanded = false
                    }
                }

                override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                    isPlaying = isPlayingNow
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        videoAspect = (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    expanded = false
                    playbackFailed = true
                }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                player.release()
            }
        }
    }

    LaunchedEffect(player, expanded) {
        if (player == null) return@LaunchedEffect
        if (expanded) {
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.volume = 1f
            player.playWhenReady = true
            player.play()
        } else {
            player.repeatMode = Player.REPEAT_MODE_ONE
            player.volume = 0f
            player.seekTo(0)
            player.playWhenReady = true
            player.play()
        }
    }

    LaunchedEffect(player, expanded) {
        if (player == null || !expanded) {
            progress = 0f
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { }
            val duration = player.duration.takeIf { it > 0L } ?: 0L
            progress = if (duration > 0L) {
                (player.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            isPlaying = player.isPlaying
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = when {
            expanded -> Alignment.CenterHorizontally
            isOutgoing -> Alignment.End
            else -> Alignment.Start
        }
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (expanded) {
                        Modifier
                            .fillMaxWidth(0.92f)
                            .aspectRatio(1f)
                    } else {
                        Modifier.size(192.dp)
                    }
                )
                .clip(CircleShape)
                .background(Color.Black)
                .clickable {
                    if (player == null) {
                        onTap()
                    } else if (!expanded) {
                        player.seekTo(0)
                        progress = 0f
                        player.repeatMode = Player.REPEAT_MODE_OFF
                        player.volume = 1f
                        player.playWhenReady = true
                        player.play()
                        expanded = true
                    } else if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (player != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        TextureView(ctx).also {
                            player.setVideoTextureView(it)
                            applyCenterCropTransform(it, videoAspect)
                        }
                    },
                    update = { view ->
                        player.setVideoTextureView(view)
                        applyCenterCropTransform(view, videoAspect)
                    }
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.82f),
                    modifier = Modifier.size(42.dp)
                )
            }

            when {
                uploadProgress != null -> CircleProgress(
                    progress = uploadProgress.coerceIn(0, 100) / 100f,
                    showTrack = true
                )
                expanded -> CircleProgress(progress = progress, showTrack = false)
            }

            if (uploadProgress != null) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.46f))
                        .clickable(onClick = onCancelUpload),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel upload",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else if (expanded && isBuffering) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(44.dp), strokeWidth = 2.dp)
            } else if (!expanded && isPlaybackActive && !playbackFailed && uri != null && (player == null || isBuffering)) {
                CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.82f),
                    modifier = Modifier.size(30.dp),
                    strokeWidth = 2.dp
                )
            } else if (expanded && !isPlaying) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.48f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play circle video",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            if (expanded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(18.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.46f))
                        .clickable { expanded = false },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close circle video",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CircleProgress(progress: Float, showTrack: Boolean) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = if (showTrack) 220 else 90,
            easing = LinearEasing
        ),
        label = "circle_progress"
    )
    val trackColor = Color.White.copy(alpha = 0.18f)
    val progressColor = Color.White.copy(alpha = 0.92f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        val inset = stroke.width / 2f
        val arcSize = androidx.compose.ui.geometry.Size(
            width = size.width - stroke.width,
            height = size.height - stroke.width
        )
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        if (showTrack) {
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
        }
        drawArc(
            color = progressColor,
            startAngle = -90f,
            sweepAngle = 360f * animatedProgress,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )
    }
}

private fun Any?.toVideoUri(): Uri? {
    return when (this) {
        is Uri -> this
        is File -> Uri.fromFile(this)
        is String -> Uri.parse(this)
        else -> null
    }
}

private fun applyCenterCropTransform(view: TextureView, videoAspect: Float) {
    if (videoAspect <= 0f) return
    view.post {
        val viewWidth = view.width.toFloat()
        val viewHeight = view.height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return@post

        val viewAspect = viewWidth / viewHeight
        val scaleX: Float
        val scaleY: Float
        if (videoAspect > viewAspect) {
            scaleX = videoAspect / viewAspect
            scaleY = 1f
        } else {
            scaleX = 1f
            scaleY = viewAspect / videoAspect
        }

        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
        view.setTransform(matrix)
    }
}
