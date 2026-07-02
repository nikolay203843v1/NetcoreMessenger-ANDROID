package com.netcoremessenger.ui.anim

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

object NetcoreAnimations {
    val slideInHorizontally = tween<Float>(300, easing = FastOutSlowInEasing)
    val slideOutHorizontally = tween<Float>(300, easing = FastOutSlowInEasing)
    val fadeIn = tween<Float>(200)
    val fadeOut = tween<Float>(200)
    val scaleIn = tween<Float>(300, easing = FastOutSlowInEasing)
    val scaleOut = tween<Float>(300, easing = FastOutSlowInEasing)

    val messageAppear = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessLow
    )

    val badgePop = spring<Float>(
        dampingRatio = 0.6f,
        stiffness = Spring.StiffnessMedium
    )

    val typingDots = repeatable<Float>(
        animation = tween(600, easing = EaseInOut),
        iterations = Int.MAX_VALUE
    )

    val pulseRing = repeatable<Float>(
        animation = tween(1500, easing = EaseInOut),
        iterations = Int.MAX_VALUE
    )

    val shakeKeyframes = keyframes<Float> {
        durationMillis = 500
        0f at 0
        -10f at 50
        10f at 100
        -8f at 150
        8f at 200
        -4f at 250
        4f at 300
        0f at 400
    }
}

@Composable
fun rememberTypingDotsAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dotOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot"
    )
    return dotOffset
}

@Composable
fun rememberPulseAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    return scale
}

@Composable
fun rememberShakeAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "shake")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = NetcoreAnimations.shakeKeyframes,
            repeatMode = RepeatMode.Restart
        ),
        label = "shake"
    )
    return offset
}

fun Modifier.bouncingClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bouncingClick"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled) {
            detectTapGestures(
                onPress = {
                    if (enabled) {
                        isPressed = true
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                        }
                    }
                },
                onTap = {
                    if (enabled) {
                        onClick()
                    }
                }
            )
        }
}


