package com.netcoremessenger.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.netcoremessenger.core.call.CallManager
import com.netcoremessenger.core.call.CallState
import com.netcoremessenger.feature.auth.onboarding.OnboardingScreen
import com.netcoremessenger.feature.call.CallScreen
import com.netcoremessenger.feature.call.IncomingCallScreen
import com.netcoremessenger.feature.chat.ChatScreen
import com.netcoremessenger.feature.chatlist.ChatListScreen
import com.netcoremessenger.feature.group.CreateGroupScreen
import com.netcoremessenger.feature.group.GroupSettingsScreen
import com.netcoremessenger.feature.media.MediaViewerScreen
import com.netcoremessenger.feature.profile.EditProfileScreen
import com.netcoremessenger.feature.profile.MyProfileScreen
import com.netcoremessenger.feature.profile.ProfileScreen
import com.netcoremessenger.feature.search.SearchScreen
import com.netcoremessenger.feature.settings.SettingsScreen
import com.netcoremessenger.feature.settings.StyleSettingsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Тонкая обёртка, чтобы достать CallManager из Hilt в Compose. */
@HiltViewModel
class GlobalCallObserverVM @Inject constructor(
    val callManager: CallManager
) : ViewModel()

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = NavRoutes.Onboarding.route,
    callObserver: GlobalCallObserverVM = hiltViewModel()
) {
    val currentCall by callObserver.callManager.current.collectAsState()

    // Глобально реагируем на входящие/исходящие звонки — навигируем в нужный экран
    LaunchedEffect(currentCall?.state, currentCall?.callId) {
        val info = currentCall ?: return@LaunchedEffect
        when (info.state) {
            CallState.RINGING_IN -> {
                navController.navigate(NavRoutes.IncomingCall.createRoute(info.callId.toString())) {
                    launchSingleTop = true
                }
            }
            CallState.RINGING_OUT, CallState.CONNECTING, CallState.ACTIVE -> {
                // Если открыт IncomingCall — заменим на InCall
                val cur = navController.currentBackStackEntry?.destination?.route
                if (cur == null || cur.startsWith("call/incoming/")) {
                    navController.navigate(
                        NavRoutes.InCall.createRoute(
                            info.peerUserId.toString(),
                            video = info.isVideo,
                            chatId = info.chatId ?: 0L
                        )
                    ) {
                        popUpTo(NavRoutes.ChatList.route)
                        launchSingleTop = true
                    }
                }
            }
            else -> {}
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(tween(300)) + slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(300)
            )
        },
        exitTransition = {
            fadeOut(tween(300)) + slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(300)
            )
        },
        popEnterTransition = {
            fadeIn(tween(300)) + slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(300)
            )
        },
        popExitTransition = {
            fadeOut(tween(300)) + slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(300)
            )
        }
    ) {
        composable(NavRoutes.Onboarding.route) {
            OnboardingScreen(
                onNavigateToChatList = {
                    navController.navigate(NavRoutes.ChatList.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.ChatList.route) {
            ChatListScreen(
                onNavigateToChat = { chatId ->
                    navController.navigate(NavRoutes.Chat.createRoute(chatId))
                },
                onNavigateToSearch = {
                    navController.navigate(NavRoutes.Search.route)
                },
                onNavigateToSettings = {
                    navController.navigate(NavRoutes.Settings.route)
                },
                onNavigateToCreateGroup = {
                    navController.navigate(NavRoutes.CreateGroup.route)
                }
            )
        }

        composable(
            route = NavRoutes.Chat.route,
            arguments = listOf(navArgument("chatId") { type = NavType.LongType })
        ) { entry ->
            val chatId = entry.arguments?.getLong("chatId") ?: 0L
            ChatScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenMedia = { mediaId ->
                    navController.navigate(NavRoutes.MediaViewer.createRoute(mediaId, 0L))
                },
                onOpenAlbum = { mediaIds, index ->
                    val first = mediaIds.getOrNull(index) ?: mediaIds.firstOrNull()
                    if (first != null) {
                        navController.navigate(
                            NavRoutes.MediaViewer.createRoute(
                                mediaId = first,
                                chatId = chatId,
                                albumIds = mediaIds,
                                index = index.coerceAtLeast(0)
                            )
                        )
                    }
                },
                onCallPeer = { userId ->
                    navController.navigate(NavRoutes.InCall.createRoute(userId.toString(), chatId = chatId))
                },
                onVideoCallPeer = { userId ->
                    navController.navigate(NavRoutes.InCall.createRoute(userId.toString(), video = true, chatId = chatId))
                },
                onOpenProfile = { userId ->
                    navController.navigate(NavRoutes.Profile.createRoute(userId))
                },
                onOpenGroupSettings = { chatId ->
                    navController.navigate(NavRoutes.GroupSettings.createRoute(chatId))
                }
            )
        }

        composable(NavRoutes.Search.route) {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onChatOpened = { chatId ->
                    navController.navigate(NavRoutes.Chat.createRoute(chatId)) {
                        popUpTo(NavRoutes.ChatList.route)
                    }
                }
            )
        }

        composable(NavRoutes.CreateGroup.route) {
            CreateGroupScreen(
                onNavigateBack = { navController.popBackStack() },
                onGroupCreated = { chatId ->
                    navController.navigate(NavRoutes.Chat.createRoute(chatId)) {
                        popUpTo(NavRoutes.ChatList.route)
                    }
                }
            )
        }

        composable(
            route = NavRoutes.GroupSettings.route,
            arguments = listOf(navArgument("chatId") { type = NavType.LongType })
        ) {
            GroupSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.MyProfile.route) {
            MyProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onEdit = { navController.navigate(NavRoutes.EditProfile.route) }
            )
        }

        composable(NavRoutes.EditProfile.route) {
            EditProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoutes.Profile.route,
            arguments = listOf(navArgument("userId") { type = NavType.LongType })
        ) {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenChat = { chatId ->
                    navController.navigate(NavRoutes.Chat.createRoute(chatId)) {
                        popUpTo(NavRoutes.ChatList.route)
                    }
                },
                onCallUser = { userId ->
                    navController.navigate(NavRoutes.InCall.createRoute(userId.toString(), chatId = 0L))
                }
            )
        }

        composable(
            route = NavRoutes.MediaViewer.route,
            arguments = listOf(
                navArgument("mediaId") { type = NavType.LongType },
                navArgument("chatId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("albumIds") { type = NavType.StringType; defaultValue = "" },
                navArgument("index") { type = NavType.IntType; defaultValue = 0 }
            )
        ) { entry ->
            val mediaId = entry.arguments?.getLong("mediaId") ?: 0L
            val albumIds = entry.arguments?.getString("albumIds")
                ?.takeIf { it.isNotBlank() }
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                .orEmpty()
            val index = entry.arguments?.getInt("index") ?: 0
            MediaViewerScreen(
                mediaId = mediaId,
                albumMediaIds = albumIds,
                initialIndex = index,
                onClose = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoutes.IncomingCall.route,
            arguments = listOf(navArgument("callId") { type = NavType.StringType })
        ) {
            IncomingCallScreen(
                onAccepted = {
                    val peer = callObserver.callManager.current.value?.peerUserId
                    if (peer != null) {
                        val chatId = callObserver.callManager.current.value?.chatId ?: 0L
                        navController.navigate(NavRoutes.InCall.createRoute(peer.toString(), chatId = chatId)) {
                            popUpTo(NavRoutes.ChatList.route)
                        }
                    }
                },
                onRejected = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoutes.InCall.route,
            arguments = listOf(
                navArgument("userId") { type = NavType.LongType },
                navArgument("video") { type = NavType.BoolType; defaultValue = false },
                navArgument("chatId") { type = NavType.LongType; defaultValue = 0L }
            )
        ) {
            CallScreen(
                onCallFinished = {
                    if (!navController.popBackStack()) {
                        navController.navigate(NavRoutes.ChatList.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogin = {
                    navController.navigate(NavRoutes.Onboarding.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToProfile = {
                    navController.navigate(NavRoutes.MyProfile.route)
                },
                onNavigateToStyleSettings = {
                    navController.navigate(NavRoutes.StyleSettings.route)
                }
            )
        }

        composable(NavRoutes.StyleSettings.route) {
            StyleSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
