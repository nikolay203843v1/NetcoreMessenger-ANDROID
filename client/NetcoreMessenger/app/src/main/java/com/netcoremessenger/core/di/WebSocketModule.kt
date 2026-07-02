package com.netcoremessenger.core.di

import com.netcoremessenger.core.data.local.dao.ChatDao
import com.netcoremessenger.core.data.local.dao.UserDao
import com.netcoremessenger.core.data.repository.ChatRepository
import com.netcoremessenger.core.data.offline.MessageDeduplicator
import com.netcoremessenger.core.data.offline.OutboxManager
import com.netcoremessenger.core.data.offline.SyncManager
import com.netcoremessenger.core.data.local.dao.MessageDao
import com.netcoremessenger.core.data.local.dao.SyncStateDao
import com.netcoremessenger.core.data.remote.api.MessagesApi
import com.netcoremessenger.core.data.remote.interceptor.AuthTokenRefresher
import com.netcoremessenger.core.data.store.TokenDataStore
import com.netcoremessenger.core.data.websocket.EventRouter
import com.netcoremessenger.core.data.websocket.WebSocketManager
import com.google.gson.Gson
import okhttp3.OkHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WebSocketClient

@Module
@InstallIn(SingletonComponent::class)
object WebSocketModule {

    @Provides
    @Singleton
    fun provideEventRouter(gson: Gson): EventRouter {
        return EventRouter(gson)
    }

    @Provides
    @Singleton
    fun provideMessageDeduplicator(messageDao: MessageDao): MessageDeduplicator {
        return MessageDeduplicator(messageDao)
    }

    @Provides
    @Singleton
    fun provideOutboxManager(
        messageDao: MessageDao,
        webSocketManagerProvider: Provider<WebSocketManager>
    ): OutboxManager {
        return OutboxManager(messageDao, webSocketManagerProvider)
    }

    @Provides
    @Singleton
    fun provideSyncManager(
        messageDao: MessageDao,
        syncStateDao: SyncStateDao,
        chatDao: ChatDao,
        messagesApi: MessagesApi,
        tokenDataStore: TokenDataStore,
        webSocketManagerProvider: Provider<WebSocketManager>,
        chatRepositoryProvider: Provider<ChatRepository>
    ): SyncManager {
        return SyncManager(
            messageDao,
            syncStateDao,
            chatDao,
            messagesApi,
            tokenDataStore,
            webSocketManagerProvider,
            chatRepositoryProvider
        )
    }

    @Provides
    @Singleton
    @WebSocketClient
    fun provideWebSocketOkHttpClient(okHttpClient: OkHttpClient): OkHttpClient {
        return okHttpClient.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideWebSocketManager(
        @WebSocketClient
        okHttpClient: OkHttpClient,
        gson: Gson,
        eventRouter: EventRouter,
        outboxManager: OutboxManager,
        syncManager: SyncManager,
        messageDao: MessageDao,
        chatDao: com.netcoremessenger.core.data.local.dao.ChatDao,
        userDao: UserDao,
        chatRepositoryProvider: Provider<com.netcoremessenger.core.data.repository.ChatRepository>,
        tokenRefresher: AuthTokenRefresher
    ): WebSocketManager {
        return WebSocketManager(
            okHttpClient,
            gson,
            eventRouter,
            outboxManager,
            syncManager,
            messageDao,
            chatDao,
            userDao,
            chatRepositoryProvider,
            tokenRefresher
        )
    }
}
