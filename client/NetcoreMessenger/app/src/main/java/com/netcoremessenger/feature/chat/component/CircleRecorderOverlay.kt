package com.netcoremessenger.feature.chat.component

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@SuppressLint("MissingPermission")
@Composable
fun CircleRecorderOverlay(
    outputFile: File,
    onRecordingStarted: (Recording) -> Unit,
    onFinalized: (File) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val coroutineScope = rememberCoroutineScope()
    val recordingProfile = remember(outputFile, context) { CircleRecordingProfile.from(context) }
    val finalized = remember(outputFile) { AtomicBoolean(false) }
    val segmentFiles = remember(outputFile) { mutableListOf<File>() }
    val switchPending = remember(outputFile) { AtomicBoolean(false) }
    val activeRecording = remember(outputFile) { AtomicReference<Recording?>(null) }
    var cameraSelector by remember(outputFile) { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }
    var cameraProvider by remember(outputFile) { mutableStateOf<ProcessCameraProvider?>(null) }
    var setupStarted by remember(outputFile) { mutableStateOf(false) }
    var segmentIndex by remember(outputFile) { mutableIntStateOf(0) }
    var seconds by remember(outputFile) { mutableIntStateOf(0) }
    var visible by remember(outputFile) { mutableStateOf(false) }
    var switchPulse by remember(outputFile) { mutableStateOf(false) }
    val overlayAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(180),
        label = "circle_record_overlay_alpha"
    )
    val previewScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.84f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 360f),
        label = "circle_record_preview_scale"
    )

    LaunchedEffect(outputFile) {
        visible = true
        seconds = 0
        while (true) {
            delay(1000)
            seconds += 1
        }
    }

    DisposableEffect(outputFile) {
        onDispose {
            activeRecording.getAndSet(null)?.stop()
            cameraProvider?.unbindAll()
        }
    }

    fun startNextSegment(provider: ProcessCameraProvider, previewView: PreviewView) {
        startCircleSegment(
            context = context,
            lifecycleOwner = lifecycleOwner,
            provider = provider,
            previewView = previewView,
            cameraSelector = cameraSelector,
            recordingProfile = recordingProfile,
            outputFile = outputFile,
            segmentIndex = segmentIndex,
            executor = executor,
            onRecording = { recording ->
                activeRecording.set(recording)
                onRecordingStarted(recording)
            },
            onSegmentFinalized = { file ->
                if (file.exists() && file.length() > 0L) {
                    segmentFiles.add(file)
                }
                activeRecording.set(null)
                provider.unbindAll()
                if (switchPending.getAndSet(false)) {
                    segmentIndex += 1
                    cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                    switchPulse = !switchPulse
                    startNextSegment(provider, previewView)
                } else {
                    finalizeCircleSegments(
                        context = context,
                        outputFile = outputFile,
                        segmentFiles = segmentFiles.toList(),
                        coroutineScope = coroutineScope,
                        onFinalized = onFinalized
                    )
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha }
            .background(Color.Black.copy(alpha = 0.48f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(308.dp)
                .graphicsLayer {
                    scaleX = previewScale
                    scaleY = previewScale
                }
                .clip(CircleShape)
                .background(Color.Black)
                .border(4.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                update = { previewView ->
                    if (setupStarted) return@AndroidView
                    setupStarted = true

                    val providerFuture = ProcessCameraProvider.getInstance(context)
                    providerFuture.addListener(
                        {
                            try {
                                val provider = providerFuture.get()
                                cameraProvider = provider
                                startNextSegment(provider, previewView)
                            } catch (_: Exception) {
                                if (finalized.compareAndSet(false, true)) {
                                    onFinalized(outputFile)
                                }
                            }
                        },
                        executor
                    )
                }
            )

            RecordingBadge(
                seconds = seconds,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        }

        val switchScale by animateFloatAsState(
            targetValue = if (switchPulse) 1.12f else 1f,
            animationSpec = spring(dampingRatio = 0.58f, stiffness = 420f),
            label = "circle_camera_switch_scale"
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
                .graphicsLayer {
                    scaleX = switchScale
                    scaleY = switchScale
                }
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.94f))
                .clickable {
                    val recording = activeRecording.get()
                    if (recording != null && !switchPending.get()) {
                        switchPending.set(true)
                        recording.stop()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Switch camera",
                tint = Color.White,
                modifier = Modifier.size(23.dp)
            )
        }
    }
}

@SuppressLint("MissingPermission")
private fun startCircleSegment(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    provider: ProcessCameraProvider,
    previewView: PreviewView,
    cameraSelector: CameraSelector,
    recordingProfile: CircleRecordingProfile,
    outputFile: File,
    segmentIndex: Int,
    executor: java.util.concurrent.Executor,
    onRecording: (Recording) -> Unit,
    onSegmentFinalized: (File) -> Unit
) {
    provider.unbindAll()
    val segmentFile = circleSegmentFile(outputFile, segmentIndex)
    segmentFile.delete()

    val targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
    val preview = Preview.Builder()
        .setTargetRotation(targetRotation)
        .build()
        .also {
        it.setSurfaceProvider(previewView.surfaceProvider)
    }
    val recorder = Recorder.Builder()
        .setQualitySelector(QualitySelector.fromOrderedList(recordingProfile.qualities))
        .setTargetVideoEncodingBitRate(recordingProfile.videoBitrate)
        .setAspectRatio(AspectRatio.RATIO_4_3)
        .build()
    val videoCapture = VideoCapture.withOutput(recorder).apply {
        this.targetRotation = targetRotation
    }

    provider.bindToLifecycle(
        lifecycleOwner,
        cameraSelector,
        preview,
        videoCapture
    )

    val outputOptions = FileOutputOptions.Builder(segmentFile)
        .setDurationLimitMillis(CIRCLE_MAX_DURATION_MS)
        .setFileSizeLimit(recordingProfile.maxFileBytes)
        .build()
    val recording = videoCapture.output
        .prepareRecording(context, outputOptions)
        .withAudioEnabled()
        .start(executor) { event ->
            if (event is VideoRecordEvent.Finalize) {
                onSegmentFinalized(segmentFile)
            }
        }
    onRecording(recording)
}

private fun finalizeCircleSegments(
    context: Context,
    outputFile: File,
    segmentFiles: List<File>,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onFinalized: (File) -> Unit
) {
    coroutineScope.launch(Dispatchers.IO) {
        runCatching {
            outputFile.delete()
            when (segmentFiles.size) {
                0 -> Unit
                1 -> segmentFiles.first().copyTo(outputFile, overwrite = true)
                else -> normalizeAndConcatenateCircleSegments(context, segmentFiles, outputFile)
            }
        }
        segmentFiles.forEach { it.delete() }
        withContext(Dispatchers.Main) {
            onFinalized(outputFile)
        }
    }
}

private suspend fun normalizeAndConcatenateCircleSegments(
    context: Context,
    segmentFiles: List<File>,
    outputFile: File
) {
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val items = segmentFiles.map { file ->
                EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(file))).build()
            }
            val composition = Composition.Builder(
                EditedMediaItemSequence.Builder(items).build()
            ).build()
            var transformer: Transformer? = null
            transformer = Transformer.Builder(context)
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            if (continuation.isActive) continuation.resumeWithException(exportException)
                        }
                    }
                )
                .build()
            continuation.invokeOnCancellation {
                runCatching { transformer?.cancel() }
            }
            transformer.start(composition, outputFile.absolutePath)
        }
    }
}

private fun circleSegmentFile(outputFile: File, index: Int): File {
    return File(outputFile.parentFile, "${outputFile.nameWithoutExtension}_segment_$index.mp4")
}

private fun concatenateMp4Segments(segmentFiles: List<File>, outputFile: File) {
    require(segmentFiles.isNotEmpty()) { "No circle video segments" }

    val firstFormats = readTrackFormats(segmentFiles.first())
    val outputTrackByMime = LinkedHashMap<String, Int>()
    val outputIndexByMime = LinkedHashMap<String, Int>()
    val maxInputSize = firstFormats.values
        .map { if (it.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) it.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0 }
        .fold(1024 * 1024) { acc, size -> max(acc, size) }

    val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    try {
        firstFormats.forEach { (mime, format) ->
            outputTrackByMime[mime] = muxer.addTrack(format)
            outputIndexByMime[mime] = outputIndexByMime.size
        }
        firstFormats["video/avc"]?.let { format ->
            if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                muxer.setOrientationHint(format.getInteger(MediaFormat.KEY_ROTATION))
            }
        }
        muxer.start()

        val timeOffsetUs = LongArray(outputTrackByMime.size)
        val buffer = ByteBuffer.allocateDirect(maxInputSize.coerceAtLeast(4 * 1024 * 1024))
        val info = MediaCodec.BufferInfo()

        segmentFiles.forEach { segment ->
            val formats = readTrackFormats(segment)
            ensureCompatibleFormats(firstFormats, formats)

            formats.keys.forEach { mime ->
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(segment.absolutePath)
                    val inputTrack = findTrackByMime(extractor, mime)
                    if (inputTrack < 0) return@forEach
                    extractor.selectTrack(inputTrack)
                    val outputTrack = outputTrackByMime.getValue(mime)
                    val outputIndex = outputIndexByMime.getValue(mime)
                    var firstPtsUs: Long? = null
                    var lastPtsUs = 0L
                    var sampleCount = 0

                    while (true) {
                        buffer.clear()
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) break
                        val sampleTime = extractor.sampleTime.takeIf { it >= 0L } ?: 0L
                        val firstPts = firstPtsUs ?: sampleTime.also { firstPtsUs = it }
                        val normalizedPts = sampleTime - firstPts
                        info.set(
                            0,
                            sampleSize,
                            timeOffsetUs[outputIndex] + normalizedPts,
                            extractor.sampleFlags
                        )
                        muxer.writeSampleData(outputTrack, buffer, info)
                        lastPtsUs = normalizedPts
                        sampleCount++
                        extractor.advance()
                    }
                    if (sampleCount > 0) {
                        timeOffsetUs[outputIndex] += lastPtsUs + estimateSampleDurationUs(mime)
                    }
                } finally {
                    extractor.release()
                }
            }
        }
    } finally {
        runCatching { muxer.stop() }
        muxer.release()
    }
}

private fun readTrackFormats(file: File): LinkedHashMap<String, MediaFormat> {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(file.absolutePath)
        val formats = LinkedHashMap<String, MediaFormat>()
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                formats[mime] = format
            }
        }
        return formats
    } finally {
        extractor.release()
    }
}

private fun ensureCompatibleFormats(
    expected: Map<String, MediaFormat>,
    actual: Map<String, MediaFormat>
) {
    require(expected.keys == actual.keys) { "Circle video segments have different tracks" }
    expected.forEach { (mime, expectedFormat) ->
        val actualFormat = actual.getValue(mime)
        if (mime.startsWith("video/")) {
            require(sameIntegerFormatValue(expectedFormat, actualFormat, MediaFormat.KEY_WIDTH))
            require(sameIntegerFormatValue(expectedFormat, actualFormat, MediaFormat.KEY_HEIGHT))
        } else if (mime.startsWith("audio/")) {
            require(sameIntegerFormatValue(expectedFormat, actualFormat, MediaFormat.KEY_SAMPLE_RATE))
            require(sameIntegerFormatValue(expectedFormat, actualFormat, MediaFormat.KEY_CHANNEL_COUNT))
        }
    }
}

private fun sameIntegerFormatValue(a: MediaFormat, b: MediaFormat, key: String): Boolean {
    if (!a.containsKey(key) || !b.containsKey(key)) return true
    return a.getInteger(key) == b.getInteger(key)
}

private fun findTrackByMime(extractor: MediaExtractor, mime: String): Int {
    for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        if (format.getString(MediaFormat.KEY_MIME) == mime) return i
    }
    return -1
}

private fun estimateSampleDurationUs(mime: String): Long {
    return if (mime.startsWith("audio/")) 23_000L else 33_333L
}

private const val CIRCLE_MAX_DURATION_MS = 60_000L
private const val CIRCLE_TARGET_UPLOAD_SECONDS = 30L
private const val CIRCLE_DEFAULT_UPSTREAM_KBPS = 2_500

private data class CircleRecordingProfile(
    val qualities: List<Quality>,
    val videoBitrate: Int,
    val maxFileBytes: Long
) {
    companion object {
        private val profiles = listOf(
            CircleRecordingProfile(listOf(Quality.SD, Quality.LOWEST), 1_200_000, 10L * 1024L * 1024L),
            CircleRecordingProfile(listOf(Quality.SD, Quality.LOWEST), 800_000, 7L * 1024L * 1024L),
            CircleRecordingProfile(listOf(Quality.LOWEST), 520_000, 5L * 1024L * 1024L),
            CircleRecordingProfile(listOf(Quality.LOWEST), 360_000, 4L * 1024L * 1024L)
        )

        fun from(context: Context): CircleRecordingProfile {
            val upstreamKbps = context.currentUpstreamKbps() ?: CIRCLE_DEFAULT_UPSTREAM_KBPS
            return profiles.firstOrNull { profile ->
                profile.estimatedUploadSeconds(upstreamKbps) <= CIRCLE_TARGET_UPLOAD_SECONDS
            } ?: profiles.last()
        }

        private fun CircleRecordingProfile.estimatedUploadSeconds(upstreamKbps: Int): Long {
            val bits = maxFileBytes * 8L
            val bitsPerSecond = upstreamKbps.coerceAtLeast(1) * 1_000L
            return (bits + bitsPerSecond - 1L) / bitsPerSecond
        }
    }
}

private fun Context.currentUpstreamKbps(): Int? {
    val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val network = connectivity?.activeNetwork ?: return null
    val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
    if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return null
    return capabilities.linkUpstreamBandwidthKbps.takeIf { it > 0 }
}

@Composable
private fun RecordingBadge(seconds: Int, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "circle_record_pulse").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse),
        label = "circle_record_alpha"
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF3B30).copy(alpha = pulse))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
    }
}
