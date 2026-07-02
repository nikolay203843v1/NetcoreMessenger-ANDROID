package com.netcoremessenger.core.data.offline

import com.netcoremessenger.core.data.local.dao.MessageDao
import com.netcoremessenger.core.data.remote.dto.MessageDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageDeduplicator @Inject constructor(
    private val messageDao: MessageDao
) {
    private val processedClientIds = LinkedHashSet<String>()
    private val maxCacheSize = 1000

    suspend fun isNewMessage(message: MessageDto): Boolean {
        val clientId = message.clientId

        if (clientId != null) {
            if (processedClientIds.contains(clientId)) {
                return false
            }

            val existing = messageDao.getByClientId(clientId)
            if (existing != null) {
                addToCache(clientId)
                return false
            }

            addToCache(clientId)
        }

        val existingById = message.id?.let { messageDao.getById(it) }
        if (existingById != null) {
            return false
        }

        return true
    }

    private fun addToCache(clientId: String) {
        processedClientIds.add(clientId)
        while (processedClientIds.size > maxCacheSize) {
            processedClientIds.iterator().let {
                it.next()
                it.remove()
            }
        }
    }

    fun clearCache() {
        processedClientIds.clear()
    }
}
