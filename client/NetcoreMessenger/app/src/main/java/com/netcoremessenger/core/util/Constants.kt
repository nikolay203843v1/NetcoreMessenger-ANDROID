package com.netcoremessenger.core.util

object Constants {
    // Домен подтягивается автоматически из Telegram-канала при старте приложения
    // (см. ServerConfig.refreshFromTelegramBlocking(), вызывается в NetcoreMessengerApp
    // до создания Hilt/Retrofit).
    // Читаем через геттер (а не val), чтобы значение не зафиксировалось намертво при
    // первой инициализации класса: если домен обновится в prefs (например, foreground-
    // refresh при возврате в приложение), новые вызовы возьмут актуальный URL.
    @JvmStatic val BASE_URL: String get() = ServerConfig.baseUrl()
    @JvmStatic val WS_URL: String get() = ServerConfig.wsUrl()

    const val DATABASE_NAME = "netcore.db"

    const val MAX_MESSAGE_RETRY = 10
    const val HEARTBEAT_INTERVAL_MS = 30_000L
    const val TYPING_TIMEOUT_MS = 3_000L

    const val MAX_DISPLAY_NAME_LENGTH = 64
    const val MAX_USERNAME_LENGTH = 32
    const val MAX_BIO_LENGTH = 512
}
