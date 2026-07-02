package com.netcoremessenger.feature.chat.component

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.netcoremessenger.feature.chat.MessageUiModel
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import com.netcoremessenger.ui.anim.bouncingClickable
import com.netcoremessenger.ui.theme.GradientPrimaryStart
import com.netcoremessenger.ui.theme.GradientPrimaryEnd
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

@Composable
fun MessageInput(
    text: String,
    onTextChanged: (String) -> Unit,
    onSendTapped: () -> Unit,
    onAttachTapped: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceStop: () -> Unit,
    onVoiceCancel: () -> Unit,
    onCircleStart: () -> Unit = {},
    onCircleStop: () -> Unit = {},
    onCircleCancel: () -> Unit = {},
    isRecording: Boolean,
    selectedImageUris: List<Uri>,
    onRemoveSelectedImage: (Uri) -> Unit,
    onClearSelectedImages: () -> Unit,
    replyTo: MessageUiModel.Message?,
    onCancelReply: () -> Unit
) {
    var circleMode by remember { mutableStateOf(false) }
    var isActionPressed by remember { mutableStateOf(false) }
    var actionDragX by remember { mutableStateOf(0f) }
    val actionScale by animateFloatAsState(
        targetValue = if (isActionPressed) 1.14f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "message_action_button_scale"
    )
    val animatedDragX by animateFloatAsState(
        targetValue = actionDragX,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "record_action_drag_x"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        if (replyTo != null) {
            ReplyPreview(
                replyTo = replyTo,
                onCancel = onCancelReply
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (selectedImageUris.isNotEmpty()) {
            AttachmentDraftPreview(
                uris = selectedImageUris,
                onRemove = onRemoveSelectedImage,
                onClear = onClearSelectedImages
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRecording) {
                InlineRecordingIndicator()
            } else {
                IconButton(
                    onClick = onAttachTapped,
                    modifier = Modifier.bouncingClickable { onAttachTapped() }
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { if (!isRecording) onTextChanged(it) },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = if (isRecording) "Отпустите для отправки" else "Сообщение",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                enabled = !isRecording,
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSendTapped() })
            )

            Spacer(modifier = Modifier.width(8.dp))

            val isSendMode = text.isNotBlank() || selectedImageUris.isNotEmpty()
            AnimatedContent(
                targetState = isSendMode,
                transitionSpec = {
                    scaleIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                    scaleOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                },
                label = "send_mic_transition"
            ) { targetSend ->
                val icon = when {
                    targetSend -> Icons.AutoMirrored.Filled.Send
                    circleMode -> Icons.Default.PhotoCamera
                    else -> Icons.Default.Mic
                }
                val tint = if (targetSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .graphicsLayer {
                            scaleX = actionScale
                            scaleY = actionScale
                            translationX = animatedDragX
                        }
                        .then(
                            if (targetSend) {
                                Modifier.bouncingClickable { onSendTapped() }
                            } else {
                                Modifier.pointerInput(circleMode) {
                                    coroutineScope {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            val cancelDistance = 96.dp.toPx()
                                            var started = false
                                            var canceled = false
                                            var dragX = 0f
                                            isActionPressed = true
                                            actionDragX = 0f
                                            val startJob = launch {
                                                delay(260)
                                                started = true
                                                if (circleMode) onCircleStart() else onVoiceStart()
                                            }
                                            try {
                                                var pointerId = down.id
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                                        ?: event.changes.firstOrNull()
                                                        ?: continue
                                                    pointerId = change.id
                                                    if (change.pressed) {
                                                        dragX += change.positionChange().x
                                                        actionDragX = dragX.coerceAtMost(0f).coerceAtLeast(-cancelDistance)
                                                        if (started && !canceled && dragX <= -cancelDistance) {
                                                            canceled = true
                                                            if (circleMode) onCircleCancel() else onVoiceCancel()
                                                            change.consume()
                                                        }
                                                    } else {
                                                        startJob.cancel()
                                                        if (started && !canceled) {
                                                            if (circleMode) onCircleStop() else onVoiceStop()
                                                        } else if (!started) {
                                                            circleMode = !circleMode
                                                        }
                                                        break
                                                    }
                                                }
                                            } finally {
                                                startJob.cancel()
                                                isActionPressed = false
                                                actionDragX = 0f
                                            }
                                        }
                                    }
                                }
                            }
                        )
                        .background(
                            brush = if (targetSend) {
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
                                    )
                                )
                            } else {
                                Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                            },
                            shape = CircleShape
                        )
                        .background(
                            color = if (targetSend) Color.Transparent else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            shape = CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = if (targetSend) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = if (targetSend) "Отправить" else "Голосовое сообщение",
                        tint = if (targetSend) tint else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineRecordingIndicator() {
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        seconds = 0
        while (true) {
            delay(1000)
            seconds += 1
        }
    }
    val pulse by rememberInfiniteTransition(label = "record_pulse").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "record_pulse_alpha"
    )
    Row(
        modifier = Modifier
            .padding(start = 8.dp, end = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFFF3B30).copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF3B30).copy(alpha = pulse))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color(0xFFFF3B30)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Отмена",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AttachmentDraftPreview(
    uris: List<Uri>,
    onRemove: (Uri) -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f))
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${uris.size} ${photoWord(uris.size)}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear attachments",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(uris, key = { it.toString() }) { uri ->
                DraftThumb(uri = uri, onRemove = { onRemove(uri) })
            }
        }
    }
}

@Composable
private fun DraftThumb(uri: Uri, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

private fun photoWord(count: Int): String = when {
    count % 10 == 1 && count % 100 != 11 -> "фотография"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "фотографии"
    else -> "фотографий"
}
