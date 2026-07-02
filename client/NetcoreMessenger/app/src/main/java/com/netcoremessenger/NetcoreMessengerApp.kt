package com.netcoremessenger

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.netcoremessenger.core.call.CallState
import com.netcoremessenger.core.call.CallManager
import com.netcoremessenger.core.data.repository.AuthRepository
import com.netcoremessenger.core.data.websocket.WebSocketManager
import com.netcoremessenger.core.util.AppContextHolder
import com.netcoremessenger.core.util.ServerConfig
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NetcoreMessengerApp : Application(), ImageLoaderFactory {

    @Inject lateinit var callManager: CallManager
    @Inject lateinit var appNotificationManager: com.netcoremessenger.core.notification.AppNotificationManager
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var webSocketManager: WebSocketManager
    @Inject lateinit var okHttpClient: OkHttpClient

    override fun newImageLoader(): ImageLoader {
        // Медиа-эндпоинты требуют Authorization: Bearer <token> — используем наш
        // OkHttpClient с AuthInterceptor, иначе Coil получит 401 и покажет плейсхолдер (серый квадрат).
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .crossfade(true)
            .build()
    }
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var startedActivities = 0

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Важно делать это здесь, а не в onCreate(): Hilt инжектит поля (а значит
        // создаёт Retrofit/OkHttp через NetworkModule, который читает Constants.BASE_URL)
        // ещё до тела нашего onCreate(). Домен должен быть свежим уже к этому моменту.
        AppContextHolder.init(base)
        ServerConfig.refreshFromTelegramBlocking()
    }

    override fun onCreate() {
        super.onCreate()
        callManager.start()
        appNotificationManager.start()
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit

            override fun onActivityStarted(activity: Activity) {
                if (startedActivities++ == 0) {
                    webSocketManager.setAppForeground(true)
                    appScope.launch {
                        authRepository.connectWebSocket()
                        authRepository.syncPushToken()
                    }
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) {
                    webSocketManager.setAppForeground(false)
                    val callState = callManager.current.value?.state
                    val keepForCall = callState == CallState.RINGING_OUT ||
                        callState == CallState.RINGING_IN ||
                        callState == CallState.CONNECTING ||
                        callState == CallState.ACTIVE
                    if (!keepForCall) {
                        webSocketManager.disconnect()
                    }
                }
            }
        })
    }
}
