package com.netcoremessenger.feature.call

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.netcoremessenger.core.call.CallState
import com.netcoremessenger.core.util.Constants
import com.netcoremessenger.ui.theme.*
import com.netcoremessenger.ui.anim.bouncingClickable
import com.netcoremessenger.ui.anim.rememberPulseAnimation
import kotlinx.coroutines.delay
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CallScreen(
    onCallFinished: () -> Unit,
    vm: CallViewModel = hiltViewModel()
) {
    val state by vm.ui.collectAsState()
    val micPerm = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val cameraPerm = rememberPermissionState(Manifest.permission.CAMERA)
    val isVideoCall = state.call?.isVideo == true
    LaunchedEffect(isVideoCall) {
        if (!micPerm.status.isGranted) micPerm.launchPermissionRequest()
        if (isVideoCall && !cameraPerm.status.isGranted) cameraPerm.launchPermissionRequest()
    }
    LaunchedEffect(isVideoCall, cameraPerm.status.isGranted, state.localVideoTrack, state.call?.state) {
        if (isVideoCall && cameraPerm.status.isGranted && state.localVideoTrack == null) {
            vm.ensureVideoStarted()
        }
    }

    LaunchedEffect(state.call?.state) {
        if (state.call == null || state.call?.state == CallState.ENDED) {
            delay(400); onCallFinished()
        }
    }

    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val durationText = remember(state.call?.startedAtMs, nowTick) {
        derivedStateOf {
            val s = state.call?.startedAtMs ?: return@derivedStateOf ""
            val secs = ((nowTick - s) / 1000).toInt().coerceAtLeast(0)
            "%d:%02d".format(secs / 60, secs % 60)
        }
    }
    LaunchedEffect(state.call?.state, state.call?.startedAtMs) {
        while (state.call?.state == CallState.ACTIVE && state.call?.startedAtMs != null) {
            nowTick = System.currentTimeMillis()
            delay(1000)
        }
    }

    val pulseScale = rememberPulseAnimation()
    val isCallActive = state.call?.state == CallState.ACTIVE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF070B18), Color(0xFF121B32))
                )
            )
    ) {
        if (isVideoCall) {
            if (state.remoteVideoTrack != null) {
                WebRtcVideoView(
                    track = state.remoteVideoTrack,
                    eglContext = vm.eglContext,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF05070E)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f))
                }
            }

            state.localVideoTrack?.let { track ->
                WebRtcVideoView(
                    track = track,
                    eglContext = vm.eglContext,
                    mirror = true,
                    overlay = true,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 72.dp, end = 18.dp)
                        .size(width = 116.dp, height = 164.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(18.dp))
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 72.dp, start = 22.dp, end = 150.dp)
            ) {
                Text(
                    text = state.peer?.displayName ?: "...",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = when (state.call?.state) {
                        CallState.RINGING_OUT -> "Звоним..."
                        CallState.CONNECTING -> "Соединение..."
                        CallState.ACTIVE -> durationText.value
                        CallState.ENDED -> "Звонок завершен"
                        else -> "..."
                    },
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 100.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCallActive) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                    alpha = (1.5f - pulseScale).coerceIn(0f, 1f)
                                }
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color(0xFF00E676).copy(alpha = 0.4f), Color.Transparent)
                                    ),
                                    shape = CircleShape
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                    alpha = (1.5f - pulseScale).coerceIn(0f, 1f)
                                }
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(GradientPrimaryStart.copy(alpha = 0.4f), Color.Transparent)
                                    ),
                                    shape = CircleShape
                                )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E2841))
                            .border(
                                width = 2.dp,
                                brush = if (isCallActive) {
                                    Brush.linearGradient(listOf(Color(0xFF00E676), Color(0xFF00B0FF)))
                                } else {
                                    Brush.linearGradient(listOf(GradientPrimaryStart, GradientPrimaryEnd))
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val peer = state.peer
                        if (peer?.avatarMediaId != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data("${Constants.BASE_URL}/api/v1/media/${peer.avatarMediaId}").crossfade(true).build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(72.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = state.peer?.displayName ?: "...",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(12.dp))

                val label = when (state.call?.state) {
                    CallState.RINGING_OUT -> "Звоним..."
                    CallState.CONNECTING -> "Соединение..."
                    CallState.ACTIVE -> durationText.value
                    CallState.ENDED -> "Звонок завершен"
                    else -> "..."
                }

                Text(
                    text = label,
                    color = if (isCallActive) Color(0xFF00E676) else Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute Button
            FloatingActionButton(
                onClick = vm::toggleMute,
                modifier = Modifier
                    .size(64.dp)
                    .bouncingClickable { vm.toggleMute() },
                containerColor = if (state.isMuted) Color(0xFFE53935) else Color(0xFF1E2841),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (state.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            FloatingActionButton(
                onClick = vm::toggleSpeaker,
                modifier = Modifier
                    .size(64.dp)
                    .bouncingClickable { vm.toggleSpeaker() },
                containerColor = if (state.isSpeakerOn) Color(0xFF2E7DFF) else Color(0xFF1E2841),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (state.isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    contentDescription = "Динамик",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Hang Up Button
            FloatingActionButton(
                onClick = vm::hangUp,
                modifier = Modifier
                    .size(64.dp)
                    .bouncingClickable { vm.hangUp() },
                containerColor = Color(0xFFE53935),
                shape = CircleShape
            ) {
                Icon(Icons.Filled.CallEnd, contentDescription = "End", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun WebRtcVideoView(
    track: VideoTrack?,
    eglContext: EglBase.Context,
    modifier: Modifier = Modifier,
    mirror: Boolean = false,
    overlay: Boolean = false
) {
    val context = LocalContext.current
    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(eglContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setMirror(mirror)
            setEnableHardwareScaler(true)
            if (overlay) setZOrderMediaOverlay(true)
        }
    }

    DisposableEffect(track) {
        track?.addSink(renderer)
        onDispose { track?.removeSink(renderer) }
    }

    DisposableEffect(Unit) {
        onDispose { renderer.release() }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier
    )
}
