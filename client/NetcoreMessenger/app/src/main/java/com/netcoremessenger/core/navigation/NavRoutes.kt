package com.netcoremessenger.core.navigation

import android.net.Uri

sealed class NavRoutes(val route: String) {
    data object Onboarding : NavRoutes("auth/onboarding")

    data object ChatList : NavRoutes("chats")
    data object Search : NavRoutes("search")
    data object CreateGroup : NavRoutes("group/create")
    data object GroupSettings : NavRoutes("group/{chatId}/settings") {
        fun createRoute(chatId: Long) = "group/$chatId/settings"
    }
    data object MyProfile : NavRoutes("profile/me")
    data object EditProfile : NavRoutes("profile/me/edit")

    data object Chat : NavRoutes("chat/{chatId}") {
        fun createRoute(chatId: Long) = "chat/$chatId"
    }

    data object Profile : NavRoutes("profile/{userId}") {
        fun createRoute(userId: Long) = "profile/$userId"
    }

    data object MediaViewer : NavRoutes("media/{mediaId}?chatId={chatId}&albumIds={albumIds}&index={index}") {
        fun createRoute(
            mediaId: Long,
            chatId: Long = 0L,
            albumIds: List<Long> = emptyList(),
            index: Int = 0
        ): String {
            val albumParam = if (albumIds.isEmpty()) "" else albumIds.joinToString(",")
            return "media/$mediaId?chatId=$chatId&albumIds=${Uri.encode(albumParam)}&index=$index"
        }
    }

    data object IncomingCall : NavRoutes("call/incoming/{callId}") {
        fun createRoute(callId: String) = "call/incoming/$callId"
    }
    data object InCall : NavRoutes("call/active/{userId}?video={video}&chatId={chatId}") {
        fun createRoute(userId: String, video: Boolean = false, chatId: Long = 0L) = "call/active/$userId?video=$video&chatId=$chatId"
    }

    data object Settings : NavRoutes("settings")
    data object StyleSettings : NavRoutes("settings/style")
}
