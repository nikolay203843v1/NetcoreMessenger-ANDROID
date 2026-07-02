package com.netcoremessenger.core.data.repository

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.netcoremessenger.core.data.local.dao.MessageDao
import com.netcoremessenger.core.data.local.entity.MessageEntity
import com.netcoremessenger.core.data.remote.api.MediaApi
import com.netcoremessenger.core.data.remote.api.MessagesApi
import com.netcoremessenger.core.data.remote.dto.EditMessageRequest
import com.netcoremessenger.core.data.remote.dto.ForwardMessageRequest
import com.netcoremessenger.core.data.remote.dto.SendMessageRequest
import com.netcoremessenger.core.data.websocket.WebSocketManager
import com.netcoremessenger.core.data.store.TokenDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val messagesApi: MessagesApi,
    private val mediaApi: MediaApi,
    private val webSocketManager: WebSocketManager,
    private val tokenDataStore: TokenDataStore,
    @ApplicationContext private val context: Context
) {
    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _albumUploadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val albumUploadProgress: StateFlow<Map<String, Int>> = _albumUploadProgress.asStateFlow()
    private val albumUploadJobs = mutableMapOf<String, Job>()

    suspend fun sendImage(chatId: Long, uri: Uri): Result<MessageEntity> = runCatching {
        sendImages(chatId, listOf(uri)).first()
    }

    fun sendImagesInBackground(
        chatId: Long,
        uris: List<Uri>,
        caption: String? = null,
        albumId: String,
        replyToMsgId: Long? = null,
        onFailure: (Throwable) -> Unit = {}
    ): Job {
        val job = uploadScope.launch {
            try {
                sendImages(
                    chatId = chatId,
                    uris = uris,
                    caption = caption,
                    albumIdOverride = albumId,
                    replyToMsgId = replyToMsgId,
                    onProgress = { progress -> setAlbumProgress(albumId, progress) }
                )
            } catch (e: Exception) {
                markAlbumFailed(albumId)
                onFailure(e)
            } finally {
                albumUploadJobs.remove(albumId)
                clearAlbumProgress(albumId)
            }
        }
        albumUploadJobs[albumId] = job
        return job
    }

    fun cancelAlbumUpload(albumId: String) {
        albumUploadJobs.remove(albumId)?.cancel()
        clearAlbumProgress(albumId)
        uploadScope.launch {
            cancelPendingAlbum(albumId)
        }
    }

    suspend fun sendImages(
        chatId: Long,
        uris: List<Uri>,
        caption: String? = null,
        albumIdOverride: String? = null,
        replyToMsgId: Long? = null,
        onProgress: (Int) -> Unit = {}
    ): List<MessageEntity> {
        require(uris.isNotEmpty()) { "At least one image is required" }

        val cleanCaption = caption?.trim()?.takeIf { it.isNotBlank() }
        val albumId = albumIdOverride ?: if (uris.size > 1 || cleanCaption != null) UUID.randomUUID().toString() else null
        val clientId = UUID.randomUUID().toString()
        val currentUserId = tokenDataStore.userId.first() ?: 0L
        val resolver = context.contentResolver
        val baseTime = System.currentTimeMillis()

        val pendingMessages = uris.mapIndexed { index, uri ->
            val nextClientId = if (index == 0) clientId else UUID.randomUUID().toString()
            val now = baseTime + index
            MessageEntity(
                id = null,
                clientId = nextClientId,
                chatId = chatId,
                senderId = currentUserId,
                type = "image",
                content = uri.toString(),
                albumId = albumId,
                replyToMsgId = replyToMsgId,
                sortKey = now * 1000 + index,
                createdAt = now,
                status = "uploading",
                isDeleted = false
            ).also { messageDao.insertPreservingStatus(it) }
        }

        val uploadSources = uris.map { uri -> prepareImageUpload(uri) }
        val knownTotalBytes = uploadSources.sumOf { it.sizeBytes.coerceAtLeast(0L) }.takeIf { it > 0L }
        var completedBytes = 0L

        val captionEntity = cleanCaption?.let {
            val captionClientId = UUID.randomUUID().toString()
            val now = baseTime + uris.size
            MessageEntity(
                id = null,
                clientId = captionClientId,
                chatId = chatId,
                senderId = currentUserId,
                type = "text",
                content = it,
                albumId = albumId,
                replyToMsgId = replyToMsgId,
                sortKey = now * 1000 + uris.size,
                createdAt = now,
                status = "uploading",
                isDeleted = false
            ).also { entity -> messageDao.insertPreservingStatus(entity) }
        }

        val sentMessages = mutableListOf<MessageEntity>()
        onProgress(0)
        pendingMessages.zip(uris).forEachIndexed { index, (entity, uri) ->
            val uploadSource = uploadSources[index]
            var uploadedBytesForFile = 0L
            val body = uploadSource.requestBody(resolver) { written ->
                uploadedBytesForFile = written
                val progress = if (knownTotalBytes != null) {
                    (((completedBytes + written).toDouble() / knownTotalBytes.toDouble()) * 100)
                        .toInt()
                } else {
                    (((index.toDouble() + 0.5) / uris.size.toDouble()) * 100).toInt()
                }.coerceIn(0, 95)
                onProgress(progress)
            }
            val part = MultipartBody.Part.createFormData("file", uploadSource.fileName, body)
            val media = try {
                mediaApi.upload(part)
            } finally {
                uploadSource.cleanup()
            }
            completedBytes += if (uploadSource.sizeBytes > 0L) uploadSource.sizeBytes else uploadedBytesForFile

            withContext(NonCancellable) {
                messageDao.updateContentAndStatusByClientId(entity.clientId!!, media.id.toString(), "pending")
                val dto = messagesApi.sendMessage(
                    SendMessageRequest(
                        chatId = chatId,
                        clientId = entity.clientId,
                        type = "image",
                        content = media.id.toString(),
                        albumId = albumId,
                        replyToMsgId = replyToMsgId
                    )
                )
                messageDao.confirmSent(entity.clientId, dto.id, dto.sortKey)
            }
            sentMessages.add(entity.copy(content = media.id.toString(), status = "pending"))
        }
        onProgress(98)

        if (cleanCaption != null && captionEntity != null) {
            withContext(NonCancellable) {
                messageDao.updateStatusByClientId(captionEntity.clientId!!, "pending")
                val dto = messagesApi.sendMessage(
                    SendMessageRequest(
                        chatId = chatId,
                        clientId = captionEntity.clientId,
                        type = "text",
                        content = cleanCaption,
                        albumId = albumId,
                        replyToMsgId = replyToMsgId
                    )
                )
                messageDao.confirmSent(captionEntity.clientId, dto.id, dto.sortKey)
            }
            sentMessages.add(captionEntity.copy(status = "pending"))
        }

        return sentMessages
    }

    suspend fun cancelPendingAlbum(albumId: String) {
        messageDao.deletePendingByAlbumId(albumId)
    }

    private fun setAlbumProgress(albumId: String, progress: Int) {
        _albumUploadProgress.value = _albumUploadProgress.value + (albumId to progress.coerceIn(0, 100))
    }

    private fun clearAlbumProgress(albumId: String) {
        _albumUploadProgress.value = _albumUploadProgress.value - albumId
    }

    suspend fun cancelPendingMessage(clientId: String) {
        messageDao.deletePendingByClientId(clientId)
    }

    suspend fun markAlbumFailed(albumId: String) {
        messageDao.markAlbumFailed(albumId)
    }

    suspend fun sendVoice(chatId: Long, audioFile: File): Result<MessageEntity> = runCatching {
        val clientId = UUID.randomUUID().toString()
        val currentUserId = tokenDataStore.userId.first() ?: 0L

        // 1. Insert local pending voice message immediately
        val entity = MessageEntity(
            id = null,
            clientId = clientId,
            chatId = chatId,
            senderId = currentUserId,
            type = "voice",
            content = audioFile.absolutePath,
            albumId = null,
            replyToMsgId = null,
            sortKey = System.currentTimeMillis() * 1000,
            createdAt = System.currentTimeMillis(),
            status = "uploading",
            isDeleted = false
        )
        messageDao.insertPreservingStatus(entity)

        try {
            // 2. Perform upload
            val body = FileProgressRequestBody(audioFile, "audio/mp4") {}
            val part = MultipartBody.Part.createFormData("file", audioFile.name, body)
            val media = mediaApi.upload(part)

            // 3. Update local message content and confirm through REST.
            withContext(NonCancellable) {
                messageDao.updateContentAndStatusByClientId(clientId, media.id.toString(), "pending")
                val dto = messagesApi.sendMessage(
                    SendMessageRequest(
                        chatId = chatId,
                        clientId = clientId,
                        type = "voice",
                        content = media.id.toString(),
                        albumId = null,
                        replyToMsgId = null
                    )
                )
                messageDao.confirmSent(clientId, dto.id, dto.sortKey)
            }

            entity.copy(content = media.id.toString())
        } catch (e: Exception) {
            messageDao.updateStatusByClientId(clientId, "failed")
            throw e
        }
    }

    suspend fun sendCircle(
        chatId: Long,
        videoFile: File,
        onProgress: (Int) -> Unit = {}
    ): Result<MessageEntity> = runCatching {
        val clientId = UUID.randomUUID().toString()
        val currentUserId = tokenDataStore.userId.first() ?: 0L
        val now = System.currentTimeMillis()

        val entity = MessageEntity(
            id = null,
            clientId = clientId,
            chatId = chatId,
            senderId = currentUserId,
            type = "circle",
            content = videoFile.absolutePath,
            albumId = null,
            replyToMsgId = null,
            sortKey = now * 1000,
            createdAt = now,
            status = "uploading",
            isDeleted = false
        )
        messageDao.insertPreservingStatus(entity)

        try {
            onProgress(0)
            val body = FileProgressRequestBody(videoFile, "video/mp4") { written ->
                val progress = ((written.toDouble() / videoFile.length().coerceAtLeast(1L).toDouble()) * 100)
                    .toInt()
                    .coerceIn(0, 95)
                onProgress(progress)
            }
            val part = MultipartBody.Part.createFormData("file", videoFile.name, body)
            val media = mediaApi.upload(part)

            withContext(NonCancellable) {
                messageDao.updateContentAndStatusByClientId(clientId, media.id.toString(), "pending")
                val dto = messagesApi.sendMessage(
                    SendMessageRequest(
                        chatId = chatId,
                        clientId = clientId,
                        type = "circle",
                        content = media.id.toString(),
                        albumId = null,
                        replyToMsgId = null
                    )
                )
                messageDao.confirmSent(clientId, dto.id, dto.sortKey)
            }
            onProgress(100)
            videoFile.delete()

            entity.copy(content = media.id.toString())
        } catch (e: Exception) {
            messageDao.updateStatusByClientId(clientId, "failed")
            throw e
        }
    }

    fun observeMessages(chatId: Long): Flow<List<MessageEntity>> {
        return messageDao.getMessages(chatId)
    }

    suspend fun fetchMessages(chatId: Long, before: Long? = null): Result<List<MessageEntity>> = runCatching {
        val remoteMessages = messagesApi.getMessages(chatId, before)
        val entities = remoteMessages.map { dto ->
            MessageEntity(
                id = dto.id,
                clientId = dto.clientId,
                chatId = dto.chatId,
                senderId = dto.senderId,
                type = dto.type,
                content = dto.content,
                albumId = dto.albumId,
                replyToMsgId = dto.replyToMsgId,
                sortKey = dto.sortKey,
                createdAt = dto.createdAt,
                status = dto.status ?: "sent",
                isDeleted = false
            )
        }
        // Не трогаем pending локально — статус локального сообщения переключится сам
        // при получении message.sent. Так мы не теряем только что отправленные.
        // Просто заменяем серверные строки (REPLACE по PK = серверный id).
        messageDao.insertAll(entities)
        entities
    }

    suspend fun sendMessage(
        chatId: Long,
        type: String,
        content: String,
        albumId: String? = null,
        replyToMsgId: Long? = null
    ): Result<MessageEntity> = runCatching {
        val clientId = UUID.randomUUID().toString()
        val currentUserId = tokenDataStore.userId.first() ?: 0L

        val entity = MessageEntity(
            id = null,
            clientId = clientId,
            chatId = chatId,
            senderId = currentUserId,
            type = type,
            content = content,
            albumId = albumId,
            replyToMsgId = replyToMsgId,
            sortKey = System.currentTimeMillis() * 1000,
            createdAt = System.currentTimeMillis(),
            status = "pending",
            isDeleted = false
        )
        messageDao.insert(entity)

        val dto = messagesApi.sendMessage(
            SendMessageRequest(
                chatId = chatId,
                clientId = clientId,
                type = type,
                content = content,
                albumId = albumId,
                replyToMsgId = replyToMsgId
            )
        )
        messageDao.confirmSent(clientId, dto.id, dto.sortKey)

        entity
    }

    /** Сохранить входящее сообщение из WebSocket message.new */
    suspend fun saveIncoming(
        id: Long, clientId: String?, chatId: Long, senderId: Long,
        type: String, content: String, sortKey: Long, createdAt: Long, albumId: String? = null
    ) {
        // Если у нас уже есть локальная pending копия с таким clientId — переписать
        if (clientId != null) {
            messageDao.deletePendingByClientId(clientId)
        }
        val entity = MessageEntity(
            id = id,
            clientId = clientId,
            chatId = chatId,
            senderId = senderId,
            type = type,
            content = content,
            albumId = albumId,
            replyToMsgId = null,
            sortKey = sortKey,
            createdAt = createdAt,
            status = "sent",
            isDeleted = false
        )
        messageDao.insertPreservingStatus(entity)
    }

    suspend fun confirmSent(clientId: String, serverId: Long, sortKey: Long) {
        val existing = messageDao.getById(serverId)
        if (existing != null) {
            // Сообщение уже сохранено через message.new — просто удалим оставшуюся pending-копию
            messageDao.deletePendingByClientId(clientId)
        } else {
            messageDao.confirmSent(clientId, serverId, sortKey)
        }
    }

    suspend fun updateStatus(messageId: Long, status: String) {
        messageDao.updateStatus(messageId, status)
    }

    suspend fun updateStatuses(messageIds: List<Long>, status: String) {
        messageDao.updateStatuses(messageIds, status)
    }

    suspend fun markDeleted(messageId: Long) {
        messageDao.markDeleted(messageId)
    }

    suspend fun deleteMessage(messageId: Long): Result<Unit> = runCatching {
        messagesApi.deleteMessage(messageId)
        messageDao.markDeleted(messageId)
    }

    suspend fun editMessage(messageId: Long, content: String): Result<Unit> = runCatching {
        val dto = messagesApi.editMessage(messageId, EditMessageRequest(content))
        messageDao.updateContentById(messageId, dto.content)
    }

    suspend fun forwardMessage(messageId: Long, targetChatId: Long): Result<Unit> = runCatching {
        val dto = messagesApi.forwardMessage(
            messageId,
            ForwardMessageRequest(chatId = targetChatId)
        )
        messageDao.insertPreservingStatus(
            MessageEntity(
                id = dto.id,
                clientId = dto.clientId,
                chatId = targetChatId,
                senderId = dto.senderId,
                type = dto.type,
                content = dto.content,
                albumId = dto.albumId,
                replyToMsgId = dto.replyToMsgId,
                sortKey = dto.sortKey,
                createdAt = dto.createdAt,
                status = dto.status ?: "sent",
                isDeleted = false
            )
        )
    }

    suspend fun getPendingMessages(): List<MessageEntity> {
        return messageDao.getPendingMessages()
    }

    suspend fun incrementRetry(clientId: String, nextRetryAt: Long) {
        messageDao.incrementRetry(clientId, nextRetryAt)
    }

    suspend fun getMaxSortKey(chatId: Long): Long? {
        return messageDao.getMaxSortKey(chatId)
    }

    private fun getUriSize(uri: Uri): Long {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            if (descriptor.length > 0L) return descriptor.length
        }
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else -1L
        } ?: -1L
    }

    private fun getFileName(uri: Uri): String {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        } ?: "photo.jpg"
    }

    private fun prepareImageUpload(uri: Uri): ImageUploadSource {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val originalName = getFileName(uri)
        val originalSize = getUriSize(uri)

        if (mime == "image/gif") {
            return ImageUploadSource.UriSource(uri, mime, originalSize, originalName)
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return ImageUploadSource.UriSource(uri, mime, originalSize, originalName)
        }

        val maxSide = max(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (maxSide / sampleSize > IMAGE_UPLOAD_MAX_SIDE * 2) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: return ImageUploadSource.UriSource(uri, mime, originalSize, originalName)

        val scaled = scaleForUpload(decoded)
        if (scaled !== decoded) decoded.recycle()

        val outFile = File(context.cacheDir, "upload_${UUID.randomUUID()}.jpg")
        outFile.outputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, IMAGE_UPLOAD_JPEG_QUALITY, output)
        }
        scaled.recycle()

        if (outFile.length() <= 0L) {
            outFile.delete()
            return ImageUploadSource.UriSource(uri, mime, originalSize, originalName)
        }

        return ImageUploadSource.FileSource(
            file = outFile,
            mimeType = "image/jpeg",
            fileName = originalName.substringBeforeLast('.', "photo") + ".jpg"
        )
    }

    private fun scaleForUpload(bitmap: Bitmap): Bitmap {
        val maxSide = max(bitmap.width, bitmap.height)
        if (maxSide <= IMAGE_UPLOAD_MAX_SIDE) return bitmap

        val scale = IMAGE_UPLOAD_MAX_SIDE.toFloat() / maxSide.toFloat()
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private companion object {
        const val IMAGE_UPLOAD_MAX_SIDE = 1920
        const val IMAGE_UPLOAD_JPEG_QUALITY = 82
    }
}

private sealed class ImageUploadSource {
    abstract val mimeType: String
    abstract val sizeBytes: Long
    abstract val fileName: String

    abstract fun requestBody(resolver: ContentResolver, onProgressBytes: (Long) -> Unit): RequestBody

    open fun cleanup() = Unit

    data class UriSource(
        val uri: Uri,
        override val mimeType: String,
        override val sizeBytes: Long,
        override val fileName: String
    ) : ImageUploadSource() {
        override fun requestBody(resolver: ContentResolver, onProgressBytes: (Long) -> Unit): RequestBody {
            return ProgressRequestBody(resolver, uri, mimeType, sizeBytes, onProgressBytes)
        }
    }

    data class FileSource(
        val file: File,
        override val mimeType: String,
        override val fileName: String
    ) : ImageUploadSource() {
        override val sizeBytes: Long get() = file.length()

        override fun requestBody(resolver: ContentResolver, onProgressBytes: (Long) -> Unit): RequestBody {
            return FileProgressRequestBody(file, mimeType, onProgressBytes)
        }

        override fun cleanup() {
            file.delete()
        }
    }
}

private class ProgressRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val mimeType: String,
    private val sizeBytes: Long,
    private val onProgressBytes: (Long) -> Unit
) : RequestBody() {
    override fun contentType() = mimeType.toMediaTypeOrNull()

    override fun contentLength(): Long = sizeBytes.takeIf { it > 0L } ?: -1L

    override fun writeTo(sink: BufferedSink) {
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var uploaded = 0L
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
                uploaded += read.toLong()
                onProgressBytes(uploaded)
            }
        } ?: throw IOException("Cannot read image")
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 64 * 1024
    }
}

private class FileProgressRequestBody(
    private val file: File,
    private val mimeType: String,
    private val onProgressBytes: (Long) -> Unit
) : RequestBody() {
    override fun contentType() = mimeType.toMediaTypeOrNull()

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var uploaded = 0L
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
                uploaded += read.toLong()
                onProgressBytes(uploaded)
            }
        }
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 64 * 1024
    }
}
