package com.netcoremessenger.core.domain.usecase.message

import com.netcoremessenger.core.data.local.entity.MessageEntity
import com.netcoremessenger.core.data.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMessagesUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    operator fun invoke(chatId: Long): Flow<List<MessageEntity>> {
        return messageRepository.observeMessages(chatId)
    }
}
