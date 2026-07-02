package com.netcoremessenger.core.domain.usecase.message

import com.netcoremessenger.core.data.local.entity.MessageEntity
import com.netcoremessenger.core.data.repository.MessageRepository
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(
        chatId: Long,
        type: String,
        content: String,
        albumId: String? = null,
        replyToMsgId: Long? = null
    ): Result<MessageEntity> {
        return messageRepository.sendMessage(chatId, type, content, albumId, replyToMsgId)
    }
}
