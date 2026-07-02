package com.netcoremessenger.feature.call

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netcoremessenger.core.call.CallInfo
import com.netcoremessenger.core.call.CallManager
import com.netcoremessenger.core.call.CallState
import com.netcoremessenger.core.data.remote.api.UsersApi
import com.netcoremessenger.core.data.remote.dto.UserDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.VideoTrack
import javax.inject.Inject
import kotlin.random.Random

data class OutgoingCallUiState(
    val peer: UserDto? = null,
    val call: CallInfo? = null,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val localVideoTrack: VideoTrack? = null,
    val remoteVideoTrack: VideoTrack? = null
)

@HiltViewModel
class CallViewModel @Inject constructor(
    private val callManager: CallManager,
    private val usersApi: UsersApi,
    handle: SavedStateHandle
) : ViewModel() {

    private val peerUserId: Long = handle["userId"]!!
    private val startAsVideo: Boolean = handle["video"] ?: false
    private val chatId: Long = handle["chatId"] ?: 0L

    private val _peer = MutableStateFlow<UserDto?>(null)
    private val _muted = MutableStateFlow(false)
    private val _speaker = MutableStateFlow(callManager.isSpeakerphoneOn())

    private val baseUi = combine(
        callManager.current,
        _peer,
        _muted,
        _speaker,
        callManager.localVideoTrack
    ) { call, peer, muted, speaker, localVideo ->
        OutgoingCallUiState(
            peer = peer,
            call = call,
            isMuted = muted,
            isSpeakerOn = speaker,
            localVideoTrack = localVideo
        )
    }

    val ui: StateFlow<OutgoingCallUiState> = combine(baseUi, callManager.remoteVideoTrack) { state, remoteVideo ->
        state.copy(remoteVideoTrack = remoteVideo)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = OutgoingCallUiState(call = callManager.current.value)
    )

    init {
        viewModelScope.launch {
            runCatching { usersApi.getUser(peerUserId) }.onSuccess { _peer.value = it }
        }
        if (callManager.current.value == null || callManager.current.value?.peerUserId != peerUserId) {
            val tempCallId = Random.nextLong(1, Long.MAX_VALUE)
            callManager.startOutgoingCall(
                callId = tempCallId,
                calleeId = peerUserId,
                isVideo = startAsVideo,
                chatId = chatId.takeIf { it > 0L }
            )
        }
    }

    fun toggleMute() { _muted.value = callManager.toggleMute() }
    fun toggleSpeaker() { _speaker.value = callManager.toggleSpeakerphone() }
    fun ensureVideoStarted() { callManager.ensureLocalVideoStarted() }
    fun hangUp() { callManager.endCall(notifyPeer = true) }

    val eglContext get() = callManager.eglContext

    val isEnded: Boolean
        get() = callManager.current.value?.state == CallState.ENDED || callManager.current.value == null
}
