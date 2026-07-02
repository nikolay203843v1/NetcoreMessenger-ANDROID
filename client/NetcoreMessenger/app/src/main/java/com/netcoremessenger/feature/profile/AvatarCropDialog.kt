package com.netcoremessenger.feature.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

@Composable
fun AvatarCropDialog(
    sourceUri: Uri,
    onCancel: () -> Unit,
    onCropped: (Uri) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(sourceUri) { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var cropSizePx by remember { mutableIntStateOf(0) }
    var baseScale by remember { mutableFloatStateOf(1f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(sourceUri) {
        bitmap = withContext(Dispatchers.IO) { loadBitmap(context, sourceUri) }
    }

    LaunchedEffect(bitmap, cropSizePx) {
        val bmp = bitmap ?: return@LaunchedEffect
        if (cropSizePx <= 0) return@LaunchedEffect
        baseScale = max(cropSizePx.toFloat() / bmp.width, cropSizePx.toFloat() / bmp.height)
        zoom = 1f
        offset = Offset.Zero
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090B10))
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel, enabled = !isProcessing) {
                    Icon(Icons.Filled.Close, contentDescription = "Отмена", tint = Color.White)
                }
                IconButton(
                    onClick = {
                        val bmp = bitmap ?: return@IconButton
                        if (cropSizePx <= 0 || isProcessing) return@IconButton
                        isProcessing = true
                        scope.launch {
                            val cropped = createCroppedAvatarUri(
                                context = context,
                                bitmap = bmp,
                                cropSizePx = cropSizePx,
                                baseScale = baseScale,
                                zoom = zoom,
                                offset = offset
                            )
                            isProcessing = false
                            onCropped(cropped)
                        }
                    },
                    enabled = bitmap != null && !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.Filled.Check, contentDescription = "Применить", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val density = LocalDensity.current
                    val cropSize = maxWidth.coerceAtMost(340.dp)
                    val bmp = bitmap

                    Box(
                        modifier = Modifier
                            .size(cropSize)
                            .onSizeChanged { cropSizePx = it.width }
                            .graphicsLayer { clip = true }
                            .pointerInput(bmp, cropSizePx) {
                                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                                    val image = bitmap ?: return@detectTransformGestures
                                    val oldZoom = zoom
                                    val nextZoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                                    val cropCenter = Offset(cropSizePx / 2f, cropSizePx / 2f)
                                    val centroidFromCenter = centroid - cropCenter
                                    val scaledOffset = (offset + centroidFromCenter) * (nextZoom / oldZoom) - centroidFromCenter
                                    val nextOffset = clampAvatarOffset(
                                        offset = scaledOffset + pan,
                                        bitmap = image,
                                        cropSizePx = cropSizePx,
                                        baseScale = baseScale,
                                        zoom = nextZoom
                                    )
                                    zoom = nextZoom
                                    offset = nextOffset
                                }
                            }
                            .background(Color.Black)
                    ) {
                        if (bmp == null) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                color = Color.White
                            )
                        } else {
                            val imageWidth = with(density) { (bmp.width * baseScale).toDp() }
                            val imageHeight = with(density) { (bmp.height * baseScale).toDp() }
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(width = imageWidth, height = imageHeight)
                                    .graphicsLayer {
                                        translationX = offset.x
                                        translationY = offset.y
                                        scaleX = zoom
                                        scaleY = zoom
                                    }
                            )
                        }

                        val primaryColor = MaterialTheme.colorScheme.primary
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val radius = size.minDimension / 2f
                            val mask = Path().apply {
                                addRect(Rect(0f, 0f, size.width, size.height))
                                addOval(
                                    Rect(
                                        center.x - radius,
                                        center.y - radius,
                                        center.x + radius,
                                        center.y + radius
                                    )
                                )
                                fillType = PathFillType.EvenOdd
                            }
                            drawPath(mask, Color.Black.copy(alpha = 0.48f))
                            drawCircle(
                                color = Color.White.copy(alpha = 0.92f),
                                radius = radius - 1.dp.toPx(),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(
                                color = primaryColor.copy(alpha = 0.75f),
                                radius = radius - 5.dp.toPx(),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

private fun clampAvatarOffset(
    offset: Offset,
    bitmap: Bitmap,
    cropSizePx: Int,
    baseScale: Float,
    zoom: Float
): Offset {
    if (cropSizePx <= 0) return Offset.Zero
    val width = bitmap.width * baseScale * zoom
    val height = bitmap.height * baseScale * zoom
    val maxX = ((width - cropSizePx) / 2f).coerceAtLeast(0f)
    val maxY = ((height - cropSizePx) / 2f).coerceAtLeast(0f)
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY)
    )
}

private fun loadBitmap(context: Context, uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
}

private suspend fun createCroppedAvatarUri(
    context: Context,
    bitmap: Bitmap,
    cropSizePx: Int,
    baseScale: Float,
    zoom: Float,
    offset: Offset
): Uri = withContext(Dispatchers.IO) {
    val displayScale = baseScale * zoom
    val cropSide = cropSizePx / displayScale
    val left = (bitmap.width / 2f) + ((-cropSizePx / 2f - offset.x) / displayScale)
    val top = (bitmap.height / 2f) + ((-cropSizePx / 2f - offset.y) / displayScale)

    val safeLeft = left.coerceIn(0f, bitmap.width - cropSide)
    val safeTop = top.coerceIn(0f, bitmap.height - cropSide)
    val src = AndroidRect(
        safeLeft.toInt(),
        safeTop.toInt(),
        (safeLeft + cropSide).toInt(),
        (safeTop + cropSide).toInt()
    )

    val outputSize = 768
    val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    canvas.drawBitmap(bitmap, src, RectF(0f, 0f, outputSize.toFloat(), outputSize.toFloat()), paint)

    val file = File(context.cacheDir, "avatar_crop_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { stream ->
        output.compress(Bitmap.CompressFormat.JPEG, 94, stream)
    }
    output.recycle()
    Uri.fromFile(file)
}
