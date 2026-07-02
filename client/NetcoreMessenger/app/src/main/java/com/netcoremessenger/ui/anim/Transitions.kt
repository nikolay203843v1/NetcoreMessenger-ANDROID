package com.netcoremessenger.ui.anim

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

object NetcoreTransitions {
    val enterTransition: EnterTransition =
        fadeIn(tween(300)) + slideInHorizontally(
            initialOffsetX = { fullWidth -> -fullWidth },
            animationSpec = tween(300)
        )

    val exitTransition: ExitTransition =
        fadeOut(tween(300)) + slideOutHorizontally(
            targetOffsetX = { fullWidth -> -fullWidth },
            animationSpec = tween(300)
        )

    val popEnterTransition: EnterTransition =
        fadeIn(tween(300)) + slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(300)
        )

    val popExitTransition: ExitTransition =
        fadeOut(tween(300)) + slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(300)
        )
}
