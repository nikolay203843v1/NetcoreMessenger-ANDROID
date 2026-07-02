package com.netcoremessenger.core.call

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.netcoremessenger.core.data.websocket.WebSocketEvent
import com.netcoremessenger.core.data.websocket.WebSocketManager
import com.netcoremessenger.core.data.websocket.ConnectionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import javax.inject.Inject
import javax.inject.Singleton

enum class CallState { IDLE, RINGING_OUT, RINGING_IN, CONNECTING, ACTIVE, ENDED }

data class CallInfo(
    val callId: Long,
    val peerUserId: Long,
    val isCaller: Boolean,
    val isVideo: Boolean = false,
    val state: CallState = CallState.IDLE,
    val startedAtMs: Long? = null,
    val chatId: Long? = null
)

@Singleton
class CallManager @Inject constructor(
    private val pcfProvider: PeerConnectionFactoryProvider,
    private val webSocketManager: WebSocketManager,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _current = MutableStateFlow<CallInfo?>(null)
    val current: StateFlow<CallInfo?> = _current.asStateFlow()
    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()
    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null
    private var localVideoTrackInternal: VideoTrack? = null
    private var localVideoSource: VideoSource? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var pendingRemoteIce: MutableList<IceCandidate> = mutableListOf()
    private var pendingRemoteOffer: WebSocketEvent.WebRtcOffer? = null
    private var pendingRemoteAnswer: WebSocketEvent.WebRtcAnswer? = null
    private var wsJob: Job? = null
    private var iceRestartAttempted = false
    private val callSignalMutex = Mutex()

    // --- Audio routing ---
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var savedAudioMode: Int = AudioManager.MODE_NORMAL
    private var savedIsSpeakerphone: Boolean = false
    private var audioConfigured = false

    private fun acquireAudio(isVideo: Boolean) {
        if (audioConfigured) return
        try {
            savedAudioMode = audioManager.mode
            savedIsSpeakerphone = audioManager.isSpeakerphoneOn
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            // Для аудио-звонка — динамик у уха, для видео — громкая связь
            audioManager.isSpeakerphoneOn = isVideo
            audioConfigured = true
        } catch (_: Throwable) {}
    }

    private fun releaseAudio() {
        if (!audioConfigured) return
        try {
            audioManager.mode = savedAudioMode
            audioManager.isSpeakerphoneOn = savedIsSpeakerphone
        } catch (_: Throwable) {}
        audioConfigured = false
    }

    /** Переключить динамик/наушник во время активного звонка. */
    fun toggleSpeakerphone(): Boolean {
        return try {
            val newVal = !audioManager.isSpeakerphoneOn
            audioManager.isSpeakerphoneOn = newVal
            newVal
        } catch (_: Throwable) { false }
    }

    fun isSpeakerphoneOn(): Boolean {
        return try {
            audioManager.isSpeakerphoneOn
        } catch (_: Throwable) {
            false
        }
    }

    val eglContext get() = pcfProvider.eglBase.eglBaseContext

    // --- Connection watchdog ---
    private var connectTimeoutJob: Job? = null
    private fun startConnectTimeout(timeoutMs: Long = 45_000L) {
        connectTimeoutJob?.cancel()
        connectTimeoutJob = scope.launch {
            delay(timeoutMs)
            val st = _current.value?.state
            if (st == CallState.RINGING_OUT || st == CallState.CONNECTING) {
                // Так и не дозвонились / не установили P2P — отбой
                endCall(notifyPeer = true)
            }
        }
    }
    private fun cancelConnectTimeout() {
        connectTimeoutJob?.cancel()
        connectTimeoutJob = null
    }

    fun start() {
        if (wsJob != null) return
        wsJob = scope.launch {
            webSocketManager.events.collect { ev ->
                when (ev) {
                    is WebSocketEvent.CallRinging -> onIncomingCall(ev.callId, ev.callerId, ev.callType == "video", ev.chatId)
                    is WebSocketEvent.CallStart -> setServerCallId(ev.callId)
                    is WebSocketEvent.CallAccept -> onPeerAccepted()
                    is WebSocketEvent.CallReject -> endCall(notifyPeer = false)
                    is WebSocketEvent.CallEnded -> endCall(notifyPeer = false)
                    is WebSocketEvent.WebRtcOffer -> onRemoteOffer(ev)
                    is WebSocketEvent.WebRtcAnswer -> onRemoteAnswer(ev)
                    is WebSocketEvent.WebRtcIce -> onRemoteIce(ev)
                    else -> {}
                }
            }
        }
    }

    private val iceServers = listOf(
        // STUN — определение публичного адреса
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        // TURN — релей через сервер, нужен когда P2P не пробивает NAT (мобильный интернет)
        // Бесплатные публичные TURN от Metered.ca — для prod лучше свой coturn или платный.
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
            .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
            .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
            .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer()
    )

    fun startOutgoingCall(callId: Long, calleeId: Long, isVideo: Boolean = false, chatId: Long? = null) {
        iceRestartAttempted = false
        _current.value = CallInfo(
            callId = callId,
            peerUserId = calleeId,
            isCaller = true,
            isVideo = isVideo,
            state = CallState.RINGING_OUT,
            chatId = chatId
        )
        acquireAudio(isVideo)
        startCallService(isVideo)
        startConnectTimeout()
        sendCallEvent(
            "call.start",
            buildMap {
                put("call_id", callId)
                put("callee_id", calleeId)
                put("call_type", if (isVideo) "video" else "audio")
                chatId?.let { put("chat_id", it) }
            }
        )
    }

    /** Сценарий: уже создан звонок на сервере (event call.start ответил callId), повторно вызвать с реальным id. */
    fun setServerCallId(callId: Long) {
        _current.value = _current.value?.copy(callId = callId)
    }

    private fun onIncomingCall(callId: Long, callerUserId: Long, isVideo: Boolean, chatId: Long? = null) {
        if (_current.value != null && _current.value?.state != CallState.IDLE) {
            // Уже занят — реджектим
            sendCallEvent("call.reject", mapOf("call_id" to callId))
            return
        }
        iceRestartAttempted = false
        _current.value = CallInfo(
            callId = callId,
            peerUserId = callerUserId,
            isCaller = false,
            isVideo = isVideo,
            state = CallState.RINGING_IN,
            chatId = chatId
        )
    }

    fun restoreIncomingCall(callId: Long, callerUserId: Long, isVideo: Boolean, chatId: Long? = null) {
        if (_current.value != null && _current.value?.state != CallState.IDLE) return
        iceRestartAttempted = false
        _current.value = CallInfo(
            callId = callId,
            peerUserId = callerUserId,
            isCaller = false,
            isVideo = isVideo,
            state = CallState.RINGING_IN,
            chatId = chatId
        )
    }

    fun acceptIncoming() {
        val info = _current.value ?: return
        if (info.isCaller || info.state != CallState.RINGING_IN) return
        _current.value = info.copy(state = CallState.CONNECTING)
        acquireAudio(info.isVideo)
        startCallService(info.isVideo)
        startConnectTimeout()
        createPeerConnection(callee = true)
        sendCallEvent("call.accept", mapOf("call_id" to info.callId))
    }

    fun rejectIncoming() {
        val info = _current.value ?: return
        sendCallEvent("call.reject", mapOf("call_id" to info.callId))
        endCall(notifyPeer = false)
    }

    @Volatile private var endingInProgress = false
    fun endCall(notifyPeer: Boolean = true) {
        if (endingInProgress) return
        endingInProgress = true
        cancelConnectTimeout()
        val info = _current.value
        if (info != null && notifyPeer) {
            try {
                sendCallEvent("call.end", mapOf("call_id" to info.callId))
            } catch (_: Throwable) {}
        }
        try { peerConnection?.close() } catch (_: Throwable) {}
        peerConnection = null
        localAudioTrack = null
        remoteAudioTrack = null
        stopLocalVideo()
        _remoteVideoTrack.value = null
        pendingRemoteIce.clear()
        pendingRemoteOffer = null
        pendingRemoteAnswer = null
        iceRestartAttempted = false
        releaseAudio()
        stopCallService()
        _current.value = info?.copy(state = CallState.ENDED)
        scope.launch {
            kotlinx.coroutines.delay(800)
            _current.value = null
            endingInProgress = false
        }
    }

    private fun createPeerConnection(callee: Boolean) {
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "signaling=$state")
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "iceConnection=$state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        cancelConnectTimeout()
                        if (_current.value?.state != CallState.ACTIVE) {
                            _current.value = _current.value?.copy(
                                state = CallState.ACTIVE,
                                startedAtMs = _current.value?.startedAtMs ?: System.currentTimeMillis()
                            )
                        }
                    }
                    // DISCONNECTED — это НОРМАЛЬНО при пере-ICE/смене сети.
                    // При первом FAILED пробуем ICE restart: на мобильных NAT первый проход часто
                    // падает, а повторная пара кандидатов через relay поднимает звонок.
                    PeerConnection.IceConnectionState.FAILED -> {
                        scheduleIceRestart()
                    }
                    PeerConnection.IceConnectionState.CLOSED -> {
                        endCall(notifyPeer = true)
                    }
                    else -> { /* CHECKING / DISCONNECTED / NEW — терпим */ }
                }
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "iceGathering=$p0")
            }
            override fun onIceCandidate(candidate: IceCandidate) {
                val to = _current.value?.peerUserId ?: return
                webSocketManager.send(
                    "webrtc.ice",
                    mapOf(
                        "to_user_id" to to,
                        "call_id" to (_current.value?.callId ?: 0L),
                        "sdpMid" to candidate.sdpMid,
                        "sdpMLineIndex" to candidate.sdpMLineIndex,
                        "candidate" to candidate.sdp
                    )
                )
            }
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {
                Log.d(TAG, "remote stream added")
                stream?.audioTracks?.firstOrNull()?.let { remoteAudioTrack = it; it.setEnabled(true) }
            }
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                Log.d(TAG, "remote track added: ${receiver?.track()?.kind()}")
                (receiver?.track() as? AudioTrack)?.let { remoteAudioTrack = it; it.setEnabled(true) }
                (receiver?.track() as? VideoTrack)?.let { track ->
                    track.setEnabled(true)
                    _remoteVideoTrack.value = track
                }
            }
        }
        val pc = pcfProvider.factory.createPeerConnection(config, observer) ?: return
        peerConnection = pc

        // Локальный аудио-трек
        val audioConstraints = MediaConstraints()
        val audioSource = pcfProvider.factory.createAudioSource(audioConstraints)
        val track = pcfProvider.factory.createAudioTrack("audio0", audioSource)
        track.setEnabled(true)
        pc.addTrack(track, listOf("stream0"))
        localAudioTrack = track

        if (_current.value?.isVideo == true) {
            startLocalVideo(pc)
        }

        pendingRemoteAnswer?.let {
            pendingRemoteAnswer = null
            onRemoteAnswer(it)
        }
        pendingRemoteOffer?.let {
            pendingRemoteOffer = null
            onRemoteOffer(it)
        }
    }

    private fun createOffer(iceRestart: Boolean = false) {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", (_current.value?.isVideo == true).toString()))
            if (iceRestart) {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }
        }
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp == null) return
                pc.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        val to = _current.value?.peerUserId ?: return
                        webSocketManager.send(
                            "webrtc.offer",
                            mapOf(
                                "to_user_id" to to,
                                "call_id" to (_current.value?.callId ?: 0L),
                                "sdp" to sdp.description
                            )
                        )
                    }
                }, sdp)
            }
        }, constraints)
    }

    private fun onPeerAccepted() {
        _current.value = _current.value?.copy(state = CallState.CONNECTING)
        createPeerConnection(callee = false)
        createOffer()
    }

    private fun scheduleIceRestart() {
        val info = _current.value
        val pc = peerConnection
        if (info == null || pc == null || iceRestartAttempted || info.state == CallState.ENDED) {
            endCall(notifyPeer = true)
            return
        }
        iceRestartAttempted = true
        Log.d(TAG, "iceConnection=FAILED; trying ICE restart")
        scope.launch {
            delay(500)
            if (_current.value?.state == CallState.ENDED || peerConnection == null) return@launch
            try {
                createOffer(iceRestart = true)
            } catch (e: Throwable) {
                Log.e(TAG, "ICE restart failed", e)
                endCall(notifyPeer = true)
            }
        }
    }

    private fun onRemoteOffer(event: WebSocketEvent.WebRtcOffer) {
        val current = _current.value
        if (current == null) {
            _current.value = CallInfo(
                callId = event.callId,
                peerUserId = event.fromUserId,
                isCaller = false,
                isVideo = _current.value?.isVideo ?: false,
                state = CallState.CONNECTING
            )
            acquireAudio(isVideo = _current.value?.isVideo == true)
            startConnectTimeout()
        }

        if (peerConnection == null) {
            pendingRemoteOffer = event
            createPeerConnection(callee = true)
            return
        }

        val pc = peerConnection ?: return
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Log.d(TAG, "remote offer set")
                drainPendingIce()
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", (_current.value?.isVideo == true).toString()))
                }
                pc.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(answer: SessionDescription?) {
                        if (answer == null) return
                        pc.setLocalDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                Log.d(TAG, "local answer set")
                                val to = _current.value?.peerUserId ?: return
                                webSocketManager.send(
                                    "webrtc.answer",
                                    mapOf(
                                        "to_user_id" to to,
                                        "call_id" to (_current.value?.callId ?: 0L),
                                        "sdp" to answer.description
                                    )
                                )
                            }
                        }, answer)
                    }
                }, constraints)
            }
        }, SessionDescription(SessionDescription.Type.OFFER, event.sdp))
    }

    private fun onRemoteAnswer(event: WebSocketEvent.WebRtcAnswer) {
        val pc = peerConnection
        if (pc == null) {
            pendingRemoteAnswer = event
            return
        }
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Log.d(TAG, "remote answer set")
                drainPendingIce()
            }
        }, SessionDescription(SessionDescription.Type.ANSWER, event.sdp))
    }

    private fun onRemoteIce(event: WebSocketEvent.WebRtcIce) {
        val ice = IceCandidate(event.sdpMid ?: "0", event.sdpMLineIndex, event.candidate)
        val pc = peerConnection
        if (pc == null || pc.remoteDescription == null) {
            pendingRemoteIce.add(ice)
        } else {
            pc.addIceCandidate(ice)
        }
    }

    private fun drainPendingIce() {
        val pc = peerConnection ?: return
        pendingRemoteIce.forEach { pc.addIceCandidate(it) }
        pendingRemoteIce.clear()
    }

    fun toggleMute(): Boolean {
        val t = localAudioTrack ?: return false
        t.setEnabled(!t.enabled())
        return !t.enabled()
    }

    private fun sendCallEvent(event: String, data: Map<String, Any>) {
        scope.launch {
            callSignalMutex.withLock {
                if (webSocketManager.connectionState.value != ConnectionState.CONNECTED) {
                    withTimeoutOrNull(8_000L) {
                        webSocketManager.connectionState.first { it == ConnectionState.CONNECTED }
                    }
                }
                webSocketManager.send(event, data)
            }
        }
    }

    fun ensureLocalVideoStarted() {
        val pc = peerConnection ?: return
        if (_current.value?.isVideo == true) {
            startLocalVideo(pc)
        }
    }

    private fun startLocalVideo(pc: PeerConnection) {
        if (localVideoTrackInternal != null) return
        val capturer = createCameraCapturer() ?: return
        val helper = SurfaceTextureHelper.create("netcore-camera", pcfProvider.eglBase.eglBaseContext)
        val source = pcfProvider.factory.createVideoSource(false)
        try {
            capturer.initialize(helper, context, source.capturerObserver)
            capturer.startCapture(1280, 720, 30)
        } catch (e: Throwable) {
            Log.e(TAG, "Video capture failed", e)
            return
        }
        val track = pcfProvider.factory.createVideoTrack("video0", source)
        track.setEnabled(true)
        pc.addTrack(track, listOf("stream0"))
        localVideoSource = source
        videoCapturer = capturer
        surfaceTextureHelper = helper
        localVideoTrackInternal = track
        _localVideoTrack.value = track
    }

    private fun stopLocalVideo() {
        _localVideoTrack.value = null
        try {
            localVideoTrackInternal?.setEnabled(false)
            localVideoTrackInternal?.dispose()
        } catch (_: Throwable) {}
        localVideoTrackInternal = null
        try { videoCapturer?.stopCapture() } catch (_: Throwable) {}
        try { videoCapturer?.dispose() } catch (_: Throwable) {}
        try { localVideoSource?.dispose() } catch (_: Throwable) {}
        try { surfaceTextureHelper?.dispose() } catch (_: Throwable) {}
        videoCapturer = null
        localVideoSource = null
        surfaceTextureHelper = null
    }

    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val names = enumerator.deviceNames
        names.firstOrNull { enumerator.isFrontFacing(it) }?.let { name ->
            enumerator.createCapturer(name, null)?.let { return it }
        }
        names.firstOrNull()?.let { name ->
            enumerator.createCapturer(name, null)?.let { return it }
        }
        return null
    }

    private fun startCallService(isVideo: Boolean) {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CallForegroundService::class.java).apply {
                    putExtra(CallForegroundService.EXTRA_IS_VIDEO, isVideo)
                }
            )
        }
    }

    private fun stopCallService() {
        runCatching {
            context.stopService(Intent(context, CallForegroundService::class.java))
        }
    }
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {
        Log.e("CallManager", "SDP create failed: $p0")
    }
    override fun onSetFailure(p0: String?) {
        Log.e("CallManager", "SDP set failed: $p0")
    }
}

private const val TAG = "CallManager"
