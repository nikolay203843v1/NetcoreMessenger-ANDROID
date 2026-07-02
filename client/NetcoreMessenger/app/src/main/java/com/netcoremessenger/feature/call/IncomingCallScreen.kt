package com.netcoremessenger.feature.call

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Person
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.netcoremessenger.core.call.CallManager
import com.netcoremessenger.core.call.CallState
import com.netcoremessenger.core.util.Constants
import com.netcoremessenger.core.data.remote.api.UsersApi
import com.netcoremessenger.core.data.remote.dto.UserDto
import com.netcoremessenger.ui.theme.*
import com.netcoremessenger.ui.anim.bouncingClickable
import com.netcoremessenger.ui.anim.rememberPulseAnimation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

@HiltViewModel
class IncomingCallViewModel @Inject constructor(
    val callManager: CallManager,
    private val usersApi: UsersApi
) : ViewModel() {

    private val _peer = MutableStateFlow<UserDto?>(null)
    val peer: StateFlow<UserDto?> = _peer.asStateFlow()
    val call = callManager.current

    init {
        viewModelScope.launch {
            call.collect { info ->
                val pid = info?.peerUserId
                if (pid != null && _peer.value?.id != pid) {
                    runCatching { usersApi.getUser(pid) }.onSuccess { _peer.value = it }
                }
            }
        }
    }

    fun accept() = callManager.acceptIncoming()
    fun reject() = callManager.rejectIncoming()
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun IncomingCallScreen(
    onAccepted: () -> Unit,
    onRejected: () -> Unit,
    vm: IncomingCallViewModel = hiltViewModel()
) {
    val peer by vm.peer.collectAsState()
    val call by vm.call.collectAsState()
    val micPerm = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val cameraPerm = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(call?.state) {
        when (call?.state) {
            CallState.CONNECTING, CallState.ACTIVE -> onAccepted()
            CallState.ENDED, null -> onRejected()
            else -> {}
        }
    }

    val pulseScale = rememberPulseAnimation()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF070B18), Color(0xFF121B32))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated Pulsing Avatar
            Box(
                modifier = Modifier.size(180.dp),
                contentAlignment = Alignment.Center
            ) {
                // Pulsing outer ring
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
                                colors = listOf(GradientPrimaryStart.copy(alpha = 0.5f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                )

                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2841))
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(listOf(GradientPrimaryStart, GradientPrimaryEnd)),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val avatarId = peer?.avatarMediaId
                    if (avatarId != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data("${Constants.BASE_URL}/api/v1/media/$avatarId").crossfade(true).build(),
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
                text = peer?.displayName ?: "Входящий звонок",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = if (call?.isVideo == true) "Входящий видеозвонок" else "Входящий звонок",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reject Button
            FloatingActionButton(
                onClick = vm::reject,
                modifier = Modifier
                    .size(64.dp)
                    .bouncingClickable { vm.reject() },
                containerColor = Color(0xFFE53935),
                shape = CircleShape
            ) {
                Icon(Icons.Filled.CallEnd, contentDescription = "Reject", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            
            // Accept Button
            FloatingActionButton(
                onClick = {
                    if (!micPerm.status.isGranted) {
                        micPerm.launchPermissionRequest()
                        return@FloatingActionButton
                    }
                    if (call?.isVideo == true && !cameraPerm.status.isGranted) {
                        cameraPerm.launchPermissionRequest()
                        return@FloatingActionButton
                    }
                    vm.accept()
                },
                modifier = Modifier
                    .size(64.dp)
                    .bouncingClickable {
                        if (!micPerm.status.isGranted) {
                            micPerm.launchPermissionRequest()
                            return@bouncingClickable
                        }
                        if (call?.isVideo == true && !cameraPerm.status.isGranted) {
                            cameraPerm.launchPermissionRequest()
                            return@bouncingClickable
                        }
                        vm.accept()
                    },
                containerColor = Color(0xFF43A047),
                shape = CircleShape
            ) {
                Icon(Icons.Filled.Call, contentDescription = "Accept", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}
