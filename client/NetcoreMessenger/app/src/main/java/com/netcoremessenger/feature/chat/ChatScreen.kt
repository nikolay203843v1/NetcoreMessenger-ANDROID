package com.netcoremessenger.feature.chat

import android.Manifest
import androidx.camera.video.Recording
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import com.netcoremessenger.feature.chat.component.CircleRecorderOverlay
import com.netcoremessenger.feature.chat.component.DateSeparator
import com.netcoremessenger.feature.chat.component.MessageBubble
import com.netcoremessenger.feature.chat.component.MessageInput
import com.netcoremessenger.feature.chat.component.MessageToolbar
import com.netcoremessenger.feature.chat.component.ReplyPreview
import com.netcoremessenger.feature.chat.component.ScrollToBottomFAB
import com.netcoremessenger.feature.chat.component.ServiceMessage
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.netcoremessenger.ui.component.DynamicBackground
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.netcoremessenger.feature.chat.component.AlbumMessageView
import java.io.File

@OptIn(ExperimentalPermissionsApi::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    onOpenMedia: (Long) -> Unit = {},
    onOpenAlbum: (List<Long>, Int) -> Unit = { _, _ -> },
    onCallPeer: (Long) -> Unit = {},
    onVideoCallPeer: (Long) -> Unit = {},
    onOpenProfile: (Long) -> Unit = {},
    onOpenGroupSettings: (Long) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val micPerm = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val cameraPerm = rememberPermissionState(Manifest.permission.CAMERA)
    var circleOutputFile by remember { mutableStateOf<File?>(null) }
    var circleRecording by remember { mutableStateOf<Recording?>(null) }
    var sendCircleOnFinalize by remember { mutableStateOf(false) }
    var circleStopRequested by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.onImagesPicked(uris.take(10))
    }

    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val showScrollFab by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            // С reverseLayout=true — низ это index 0
            listState.firstVisibleItemIndex > 3
        }
    }
    val activeCircleMessageIds by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            listState.layoutInfo.visibleItemsInfo
                .asSequence()
                .flatMap { item -> sequenceOf(item.index - 1, item.index, item.index + 1) }
                .distinct()
                .mapNotNull { index -> uiState.messages.getOrNull(index) as? MessageUiModel.Message }
                .filter { it.type == "circle" }
                .map { it.localId }
                .toSet()
        }
    }

    val newestMessageKey = uiState.messages.firstOrNull { it !is MessageUiModel.DateSeparator }?.let { item ->
        when (item) {
            is MessageUiModel.Message -> item.localId + ":" + item.status
            is MessageUiModel.Album -> item.localId + ":" + item.status + ":" + item.items.size
            is MessageUiModel.DateSeparator -> item.date.toString()
        }
    }
    var lastAutoScrollKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(newestMessageKey) {
        val key = newestMessageKey ?: return@LaunchedEffect
        val previousKey = lastAutoScrollKey
        lastAutoScrollKey = key
        if (previousKey == null || previousKey == key) {
            return@LaunchedEffect
        }
        delay(60)
        if (listState.layoutInfo.totalItemsCount > 0) {
            listState.animateScrollToItem(0)
        }
    }

    DynamicBackground {
        if (uiState.forwardMessage != null) {
            ForwardPickerDialog(
                targets = uiState.forwardTargets,
                onDismiss = viewModel::onDismissForwardPicker,
                onForward = viewModel::onForwardToChat
            )
        }

        Scaffold(
            topBar = {
                MessageToolbar(
                    title = uiState.chatTitle,
                    isOnline = uiState.isOnline,
                    avatarMediaId = uiState.chatAvatar?.toLongOrNull(),
                    onNavigateBack = onNavigateBack,
                    onTitleTap = {
                        val peer = uiState.peerUserId
                        if (peer != null) onOpenProfile(peer) else onOpenGroupSettings(uiState.chatId)
                    },
                    onAudioCall = {
                        if (!micPerm.status.isGranted) {
                            micPerm.launchPermissionRequest()
                        } else {
                            uiState.peerUserId?.let(onCallPeer)
                        }
                    },
                    onVideoCall = {
                        when {
                            !micPerm.status.isGranted -> micPerm.launchPermissionRequest()
                            !cameraPerm.status.isGranted -> cameraPerm.launchPermissionRequest()
                            else -> uiState.peerUserId?.let(onVideoCallPeer)
                        }
                    },
                    isTyping = uiState.otherUserTyping
                )
            },
            bottomBar = {
                MessageInput(
                    text = uiState.inputText,
                    onTextChanged = viewModel::onTextChanged,
                    onSendTapped = viewModel::onSendTapped,
                    onAttachTapped = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onVoiceStart = {
                        if (!micPerm.status.isGranted) {
                            micPerm.launchPermissionRequest()
                        } else {
                            viewModel.onVoiceRecordStart()
                        }
                    },
                    onVoiceStop = viewModel::onVoiceRecordStop,
                    onVoiceCancel = viewModel::onVoiceRecordCancel,
                    onCircleStart = {
                        when {
                            !micPerm.status.isGranted -> micPerm.launchPermissionRequest()
                            !cameraPerm.status.isGranted -> cameraPerm.launchPermissionRequest()
                            else -> {
                                keyboardController?.hide()
                                circleRecording?.stop()
                                circleRecording = null
                                sendCircleOnFinalize = false
                                circleStopRequested = false
                                circleOutputFile = File(context.cacheDir, "circle_${System.currentTimeMillis()}.mp4")
                            }
                        }
                    },
                    onCircleStop = {
                        sendCircleOnFinalize = true
                        circleStopRequested = true
                        circleRecording?.stop()
                    },
                    onCircleCancel = {
                        sendCircleOnFinalize = false
                        circleStopRequested = true
                        circleRecording?.stop()
                    },
                    isRecording = uiState.isRecordingVoice || circleOutputFile != null,
                    selectedImageUris = uiState.selectedImageUris,
                    onRemoveSelectedImage = viewModel::onRemoveSelectedImage,
                    onClearSelectedImages = viewModel::onClearSelectedImages,
                    replyTo = uiState.replyTo,
                    onCancelReply = viewModel::onCancelReply
                )
            },
            floatingActionButton = {
                if (showScrollFab) {
                    ScrollToBottomFAB(
                        unreadCount = 0,
                        onClick = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(0)
                            }
                        }
                    )
                }
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        count = uiState.messages.size,
                        key = { index ->
                            val item = uiState.messages[index]
                            when (item) {
                                is MessageUiModel.Message -> "msg_${item.localId}"
                                is MessageUiModel.Album -> "album_${item.localId}"
                                is MessageUiModel.DateSeparator -> "date_${item.date}"
                            }
                        }
                    ) { index ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = when (val item = uiState.messages[index]) {
                                is MessageUiModel.Message -> if (item.isOutgoing) androidx.compose.ui.Alignment.TopEnd else androidx.compose.ui.Alignment.TopStart
                                is MessageUiModel.Album -> if (item.isOutgoing) androidx.compose.ui.Alignment.TopEnd else androidx.compose.ui.Alignment.TopStart
                                is MessageUiModel.DateSeparator -> androidx.compose.ui.Alignment.TopCenter
                            }
                        ) {
                            when (val item = uiState.messages[index]) {
                                is MessageUiModel.DateSeparator -> {
                                    DateSeparator(date = item.date)
                                }
                                is MessageUiModel.Message -> {
                                    if (item.type == "service") {
                                        ServiceMessage(
                                            content = item.content,
                                            timestamp = item.timestamp,
                                            isOutgoing = item.isOutgoing
                                        )
                                    } else {
                                        MessageBubble(
                                            message = item,
                                            isHighlighted = uiState.highlightedMessageLocalId == item.localId,
                                            circleUploadProgress = uiState.circleUploadProgress.takeIf { item.type == "circle" && item.status == "uploading" },
                                            onLongPress = viewModel::onMessageLongPress,
                                            onMediaTap = {
                                                // content для image/video = mediaId
                                                item.content.toLongOrNull()?.let(onOpenMedia)
                                            },
                                            onReplyTap = viewModel::onReplyTap,
                                            onReplyPreviewTap = { reply ->
                                                val targetIndex = uiState.messages.indexOfFirst { candidate ->
                                                    candidate is MessageUiModel.Message &&
                                                        (candidate.localId == reply.localId || candidate.serverId == reply.serverId)
                                                }
                                                if (targetIndex >= 0) {
                                                    coroutineScope.launch {
                                                        listState.animateScrollToItem(targetIndex)
                                                        val target = uiState.messages[targetIndex] as? MessageUiModel.Message
                                                        target?.let { viewModel.highlightMessage(it.localId) }
                                                    }
                                                }
                                            },
                                            onCancelCircleUpload = viewModel::onCancelCircleUpload,
                                            isCirclePlaybackActive = item.type != "circle" || item.localId in activeCircleMessageIds
                                        )
                                    }
                                }
                                is MessageUiModel.Album -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = { viewModel.onAlbumLongPress(item) },
                                                onLongClick = { viewModel.onAlbumLongPress(item) }
                                            ),
                                        contentAlignment = if (item.isOutgoing) {
                                            androidx.compose.ui.Alignment.TopEnd
                                        } else {
                                            androidx.compose.ui.Alignment.TopStart
                                        }
                                    ) {
                                        AlbumMessageView(
                                            album = item,
                                            uploadProgress = if (uiState.uploadingAlbumId != null && item.localId == "album_${uiState.uploadingAlbumId}") {
                                                uiState.uploadProgress
                                            } else {
                                                null
                                            },
                                            onCancelUpload = viewModel::onCancelAlbumUpload,
                                            onLongPress = viewModel::onAlbumLongPress,
                                            onMediaTap = { index, _ ->
                                                val mediaIds = item.items.mapNotNull { photo ->
                                                    photo.content.toLongOrNull()?.takeIf { it > 0L }
                                                }
                                                onOpenAlbum(mediaIds, index.coerceIn(0, (mediaIds.size - 1).coerceAtLeast(0)))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            uiState.actionMessage?.let { selected ->
                MessageActionOverlay(
                    message = selected,
                    onDismiss = viewModel::onDismissMessageActions,
                    onReply = { viewModel.onReplyTap(selected) },
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(selected.content))
                        viewModel.onDismissMessageActions()
                    },
                    onEdit = { viewModel.onEditMessage(selected) },
                    onDelete = { viewModel.onDeleteMessage(selected) },
                    onForward = { viewModel.onForwardMessage(selected) }
                )
            }
            uiState.actionAlbum?.let { selected ->
                AlbumActionOverlay(
                    album = selected,
                    onDismiss = viewModel::onDismissMessageActions,
                    onReply = { viewModel.onReplyToAlbum(selected) },
                    onDelete = { viewModel.onDeleteAlbum(selected) }
                )
            }
        }
        }

        circleOutputFile?.let { file ->
            CircleRecorderOverlay(
                outputFile = file,
                onRecordingStarted = { recording ->
                    circleRecording = recording
                    if (circleStopRequested) {
                        recording.stop()
                    }
                },
                onFinalized = { finalizedFile ->
                    val shouldSend = sendCircleOnFinalize
                    circleOutputFile = null
                    circleRecording = null
                    sendCircleOnFinalize = false
                    circleStopRequested = false
                    if (shouldSend && finalizedFile.exists() && finalizedFile.length() > 0L) {
                        viewModel.onCircleVideoRecorded(finalizedFile)
                    } else {
                        finalizedFile.delete()
                    }
                }
            )
        }
    }
}

@Composable
private fun MessageActionOverlay(
    message: MessageUiModel.Message,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit
) {
    val canEdit = message.isOutgoing && message.type == "text" && message.serverId != null
    val canDelete = message.isOutgoing && message.serverId != null
    val canForward = message.serverId != null
    val canCopy = message.type == "text" && message.content.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.22f))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 18.dp),
        contentAlignment = if (message.isOutgoing) {
            androidx.compose.ui.Alignment.CenterEnd
        } else {
            androidx.compose.ui.Alignment.CenterStart
        }
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 190.dp, max = 240.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(18.dp)
                )
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(vertical = 6.dp)
        ) {
            ActionMenuItem(icon = Icons.AutoMirrored.Filled.Reply, text = "Ответить", onClick = onReply)
            if (canCopy) {
                ActionMenuItem(icon = Icons.Default.ContentCopy, text = "Копировать", onClick = onCopy)
            }
            if (canEdit) {
                ActionMenuItem(icon = Icons.Default.Edit, text = "Редактировать", onClick = onEdit)
            }
            if (canForward) {
                ActionMenuItem(icon = Icons.AutoMirrored.Filled.Forward, text = "Переслать", onClick = onForward)
            }
            if (canDelete) {
                ActionMenuItem(
                    icon = Icons.Default.Delete,
                    text = "Удалить",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun AlbumActionOverlay(
    album: MessageUiModel.Album,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit
) {
    val canDelete = album.isOutgoing && album.serverIds.any { it != null }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.22f))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 18.dp),
        contentAlignment = if (album.isOutgoing) {
            androidx.compose.ui.Alignment.CenterEnd
        } else {
            androidx.compose.ui.Alignment.CenterStart
        }
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 190.dp, max = 240.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(18.dp)
                )
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(vertical = 6.dp)
        ) {
            ActionMenuItem(icon = Icons.AutoMirrored.Filled.Reply, text = "Ответить", onClick = onReply)
            if (canDelete) {
                ActionMenuItem(
                    icon = Icons.Default.Delete,
                    text = "Удалить",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun ActionMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = tint
        )
    }
}

@Composable
private fun ForwardPickerDialog(
    targets: List<ForwardTargetUi>,
    onDismiss: () -> Unit,
    onForward: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переслать в чат", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                targets.take(12).forEach { target ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onForward(target.chatId) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text(
                                text = target.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = target.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
