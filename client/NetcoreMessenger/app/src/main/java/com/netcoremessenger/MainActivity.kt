package com.netcoremessenger

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.netcoremessenger.core.call.CallManager
import com.netcoremessenger.core.data.store.TokenDataStore
import com.netcoremessenger.core.navigation.NavGraph
import com.netcoremessenger.core.navigation.NavRoutes
import com.netcoremessenger.core.notification.CallActionReceiver
import com.netcoremessenger.ui.theme.NetcoreTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenDataStore: TokenDataStore
    @Inject
    lateinit var callManager: CallManager
    private var pendingChatId by mutableStateOf<Long?>(null)
    private var pendingCallRoute by mutableStateOf<PendingCallRoute?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        handleCallIntent(intent)
        pendingChatId = intent?.getLongExtra("chat_id", 0L)?.takeIf { it > 0L }
        enableEdgeToEdge()

        // Запросим разрешение на уведомления (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100
                )
            }
        }

        setContent {
            val themeMode by tokenDataStore.themeMode.collectAsState(initial = "dark")
            val accentStyle by tokenDataStore.accentStyle.collectAsState(initial = "indigo")
            val fontStyle by tokenDataStore.fontStyle.collectAsState(initial = "default")
            val darkTheme = themeMode != "light"

            NetcoreTheme(
                darkTheme = darkTheme,
                accentStyle = accentStyle,
                fontStyle = fontStyle
            ) {
                val authTokenState by tokenDataStore.accessToken
                    .map<String?, AuthTokenState> { AuthTokenState.Loaded(it) }
                    .collectAsState(initial = AuthTokenState.Loading)

                if (authTokenState is AuthTokenState.Loaded) {
                    val navController = rememberNavController()
                    val accessToken = (authTokenState as AuthTokenState.Loaded).accessToken
                    val startDestination = if (accessToken.isNullOrBlank()) {
                        NavRoutes.Onboarding.route
                    } else {
                        NavRoutes.ChatList.route
                    }
                    val backStackEntry by navController.currentBackStackEntryAsState()

                    LaunchedEffect(accessToken, backStackEntry?.destination?.route) {
                        if (accessToken.isNullOrBlank() && backStackEntry?.destination?.route != NavRoutes.Onboarding.route) {
                            navController.navigate(NavRoutes.Onboarding.route) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }

                    LaunchedEffect(pendingChatId) {
                        val chatId = pendingChatId ?: return@LaunchedEffect
                        navController.navigate(NavRoutes.Chat.createRoute(chatId)) {
                            launchSingleTop = true
                        }
                        pendingChatId = null
                    }
                    LaunchedEffect(pendingCallRoute) {
                        val route = pendingCallRoute ?: return@LaunchedEffect
                        if (route.accepted) {
                            navController.navigate(
                                NavRoutes.InCall.createRoute(
                                    route.peerUserId.toString(),
                                    route.isVideo,
                                    route.chatId ?: 0L
                                )
                            ) {
                                popUpTo(NavRoutes.ChatList.route)
                                launchSingleTop = true
                            }
                        } else {
                            navController.navigate(NavRoutes.IncomingCall.createRoute(route.callId.toString())) {
                                launchSingleTop = true
                            }
                        }
                        pendingCallRoute = null
                    }

                    NavGraph(navController = navController, startDestination = startDestination)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallIntent(intent)
        pendingChatId = intent.getLongExtra("chat_id", 0L).takeIf { it > 0L }
    }

    private fun handleCallIntent(intent: Intent?) {
        if (intent == null) return
        val callId = intent.getLongExtra(CallActionReceiver.EXTRA_CALL_ID, intent.getLongExtra("call_id", 0L))
        val callerId = intent.getLongExtra(CallActionReceiver.EXTRA_CALLER_ID, intent.getLongExtra("caller_id", 0L))
        if (callId <= 0L || callerId <= 0L) return
        val isVideo = intent.getBooleanExtra(
            CallActionReceiver.EXTRA_IS_VIDEO,
            intent.getBooleanExtra("is_video", false)
        )
        val chatId = intent.getLongExtra(CallActionReceiver.EXTRA_CHAT_ID, intent.getLongExtra("chat_id", 0L))
            .takeIf { it > 0L }
        callManager.restoreIncomingCall(callId, callerId, isVideo, chatId)
        val accepted = intent.action == CallActionReceiver.ACTION_ACCEPT
        if (accepted) {
            callManager.acceptIncoming()
        }
        val shouldOpen = accepted ||
            intent.getBooleanExtra(CallActionReceiver.EXTRA_OPEN_CALL, false) ||
            intent.getBooleanExtra("open_call", false)
        if (shouldOpen) {
            pendingCallRoute = PendingCallRoute(
                callId = callId,
                peerUserId = callerId,
                isVideo = isVideo,
                accepted = accepted,
                chatId = chatId
            )
        }
    }
}

private data class PendingCallRoute(
    val callId: Long,
    val peerUserId: Long,
    val isVideo: Boolean,
    val accepted: Boolean,
    val chatId: Long? = null
)

private sealed interface AuthTokenState {
    data object Loading : AuthTokenState
    data class Loaded(val accessToken: String?) : AuthTokenState
}
