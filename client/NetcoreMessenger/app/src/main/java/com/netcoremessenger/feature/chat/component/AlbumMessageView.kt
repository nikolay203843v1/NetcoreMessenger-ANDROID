package com.netcoremessenger.feature.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.netcoremessenger.core.util.Constants
import com.netcoremessenger.core.util.DateUtils
import com.netcoremessenger.feature.chat.MessageUiModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumMessageView(
    album: MessageUiModel.Album,
    uploadProgress: Int? = null,
    onCancelUpload: (String) -> Unit = {},
    onLongPress: (MessageUiModel.Album) -> Unit = {},
    onMediaTap: (index: Int, mediaId: Long) -> Unit
) {
    val albumId = album.items.firstOrNull()?.albumId ?: album.localId.removePrefix("album_")
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (album.isOutgoing) 18.dp else 6.dp,
        bottomEnd = if (album.isOutgoing) 6.dp else 18.dp
    )
    val maxWidth = 308.dp

    Column(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .clip(shape)
            .background(
                if (album.isOutgoing) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .padding(2.dp)
    ) {
        when (album.items.size) {
            1 -> SinglePhoto(album, 0, albumId, uploadProgress, onCancelUpload, onLongPress, onMediaTap)
            2 -> TwoPhotoRow(album, albumId, uploadProgress, onCancelUpload, onLongPress, onMediaTap)
            3 -> ThreePhotoLayout(album, albumId, uploadProgress, onCancelUpload, onLongPress, onMediaTap)
            4 -> FourPhotoGrid(album, albumId, uploadProgress, onCancelUpload, onLongPress, onMediaTap)
            else -> MultiPhotoGrid(album, albumId, uploadProgress, onCancelUpload, onLongPress, onMediaTap)
        }

        if (!album.caption.isNullOrBlank()) {
            Text(
                text = album.caption,
                style = MaterialTheme.typography.bodyMedium,
                color = if (album.isOutgoing) {
                    Color.White.copy(alpha = 0.96f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.End)
                .padding(horizontal = 8.dp, vertical = if (album.caption.isNullOrBlank()) 1.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = DateUtils.formatTime(album.timestamp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                color = if (album.isOutgoing) {
                    Color.White.copy(alpha = 0.78f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                }
            )
            if (album.isOutgoing) {
                AlbumDeliveryCheckmark(status = album.status, isOutgoing = album.isOutgoing)
            }
        }
    }
}

@Composable
private fun AlbumDeliveryCheckmark(status: String, isOutgoing: Boolean) {
    val activeColor = Color(0xFF00E676)
    val inactiveColor = if (isOutgoing) {
        Color.White.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    }

    if (status == "pending") {
        Icon(
            imageVector = Icons.Default.AccessTime,
            contentDescription = "Pending",
            tint = inactiveColor,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(11.dp)
        )
    } else {
        Row(
            modifier = Modifier.padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = status,
                tint = if (status == "read") activeColor else inactiveColor,
                modifier = Modifier.size(12.dp)
            )
            if (status == "delivered" || status == "read") {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = status,
                    tint = if (status == "read") activeColor else inactiveColor,
                    modifier = Modifier
                        .size(12.dp)
                        .offset(x = (-7).dp)
                )
            }
        }
    }
}

@Composable
private fun SinglePhoto(
    album: MessageUiModel.Album,
    index: Int,
    albumId: String,
    uploadProgress: Int?,
    onCancelUpload: (String) -> Unit,
    onLongPress: (MessageUiModel.Album) -> Unit,
    onMediaTap: (index: Int, mediaId: Long) -> Unit
) {
    val item = album.items[index]
    PhotoTile(
        item = item,
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        uploadProgress = uploadProgress,
        onCancelUpload = { onCancelUpload(albumId) },
        onLongPress = { onLongPress(album) },
        onClick = { onMediaTap(index, item.content.toLongOrNull() ?: 0L) }
    )
}

@Composable
private fun TwoPhotoRow(
    album: MessageUiModel.Album,
    albumId: String,
    uploadProgress: Int?,
    onCancelUpload: (String) -> Unit,
    onLongPress: (MessageUiModel.Album) -> Unit,
    onMediaTap: (index: Int, mediaId: Long) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        album.items.take(2).forEachIndexed { index, item ->
            PhotoTile(
                item = item,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
                uploadProgress = uploadProgress,
                onCancelUpload = { onCancelUpload(albumId) },
                onLongPress = { onLongPress(album) },
                onClick = { onMediaTap(index, item.content.toLongOrNull() ?: 0L) }
            )
        }
    }
}

@Composable
private fun ThreePhotoLayout(
    album: MessageUiModel.Album,
    albumId: String,
    uploadProgress: Int?,
    onCancelUpload: (String) -> Unit,
    onLongPress: (MessageUiModel.Album) -> Unit,
    onMediaTap: (index: Int, mediaId: Long) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val left = album.items[0]
        PhotoTile(
            item = left,
            modifier = Modifier
                .weight(1f)
                .height(242.dp),
        uploadProgress = uploadProgress,
        onCancelUpload = { onCancelUpload(albumId) },
        onLongPress = { onLongPress(album) },
        onClick = { onMediaTap(0, left.content.toLongOrNull() ?: 0L) }
    )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            album.items.drop(1).take(2).forEachIndexed { offset, item ->
                PhotoTile(
                    item = item,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    uploadProgress = uploadProgress,
                    onCancelUpload = { onCancelUpload(albumId) },
                    onLongPress = { onLongPress(album) },
                    onClick = { onMediaTap(offset + 1, item.content.toLongOrNull() ?: 0L) }
                )
            }
        }
    }
}

@Composable
private fun FourPhotoGrid(
    album: MessageUiModel.Album,
    albumId: String,
    uploadProgress: Int?,
    onCancelUpload: (String) -> Unit,
    onLongPress: (MessageUiModel.Album) -> Unit,
    onMediaTap: (index: Int, mediaId: Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        album.items.chunked(2).take(2).forEachIndexed { rowIndex, rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                rowItems.forEachIndexed { colIndex, item ->
                    val index = rowIndex * 2 + colIndex
                    PhotoTile(
                        item = item,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        uploadProgress = uploadProgress,
                        onCancelUpload = { onCancelUpload(albumId) },
                        onLongPress = { onLongPress(album) },
                        onClick = { onMediaTap(index, item.content.toLongOrNull() ?: 0L) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiPhotoGrid(
    album: MessageUiModel.Album,
    albumId: String,
    uploadProgress: Int?,
    onCancelUpload: (String) -> Unit,
    onLongPress: (MessageUiModel.Album) -> Unit,
    onMediaTap: (index: Int, mediaId: Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        album.items.chunked(3).forEachIndexed { rowIndex, rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                rowItems.forEachIndexed { colIndex, item ->
                    val index = rowIndex * 3 + colIndex
                    PhotoTile(
                        item = item,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        uploadProgress = uploadProgress,
                        onCancelUpload = { onCancelUpload(albumId) },
                        onLongPress = { onLongPress(album) },
                        onClick = { onMediaTap(index, item.content.toLongOrNull() ?: 0L) }
                    )
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(
    item: MessageUiModel.Message,
    modifier: Modifier,
    uploadProgress: Int?,
    onCancelUpload: () -> Unit,
    onLongPress: () -> Unit,
    onClick: () -> Unit
) {
    val mediaId = item.content.toLongOrNull() ?: 0L
    val imageUrl = if (mediaId > 0L) {
        "${Constants.BASE_URL}/api/v1/media/$mediaId/thumbnail"
    } else {
        item.content
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                enabled = mediaId > 0L,
                onClick = onClick,
                onLongClick = onLongPress
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (item.status == "pending" || item.status == "uploading" || item.status == "failed") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.34f)),
                contentAlignment = Alignment.Center
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (item.status == "failed") {
                        Text(
                            text = "Ошибка",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    } else if (uploadProgress != null) {
                        CircularProgressIndicator(
                            progress = { uploadProgress.coerceIn(0, 100) / 100f },
                            modifier = Modifier.size(44.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.22f),
                            strokeWidth = 3.dp
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(44.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.22f),
                            strokeWidth = 3.dp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { onCancelUpload() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel upload",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
