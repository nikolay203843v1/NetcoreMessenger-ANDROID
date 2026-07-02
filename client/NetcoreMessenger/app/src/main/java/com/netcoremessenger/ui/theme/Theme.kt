package com.netcoremessenger.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = CosmicLightPrimary,
    onPrimary = CosmicLightOnPrimary,
    primaryContainer = CosmicLightPrimary.copy(alpha = 0.1f),
    onPrimaryContainer = CosmicLightPrimary,
    secondary = CosmicLightPrimary,
    onSecondary = CosmicLightOnPrimary,
    background = CosmicLightBackground,
    onBackground = CosmicLightTextPrimary,
    surface = CosmicLightSurface,
    onSurface = CosmicLightTextPrimary,
    surfaceVariant = CosmicLightSurfaceVariant,
    onSurfaceVariant = CosmicLightTextSecondary,
    outline = CosmicLightOutline,
    outlineVariant = CosmicLightSurfaceVariant,
    error = CosmicLightError,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = CosmicDarkPrimary,
    onPrimary = CosmicDarkOnPrimary,
    primaryContainer = CosmicDarkPrimary.copy(alpha = 0.2f),
    onPrimaryContainer = CosmicDarkPrimary,
    secondary = CosmicDarkPrimary,
    onSecondary = CosmicDarkOnPrimary,
    background = CosmicDarkBackground,
    onBackground = CosmicDarkTextPrimary,
    surface = CosmicDarkSurface,
    onSurface = CosmicDarkTextPrimary,
    surfaceVariant = CosmicDarkSurfaceVariant,
    onSurfaceVariant = CosmicDarkTextSecondary,
    outline = CosmicDarkOutline,
    outlineVariant = CosmicDarkSurfaceVariant,
    error = CosmicDarkError,
    onError = Color.Black
)

private fun accentColor(style: String, darkTheme: Boolean): Color = when (style) {
    "green" -> if (darkTheme) Color(0xFF34D399) else Color(0xFF059669)
    "red" -> if (darkTheme) Color(0xFFFB7185) else Color(0xFFE11D48)
    "blue" -> if (darkTheme) Color(0xFF60A5FA) else Color(0xFF2563EB)
    "gold" -> if (darkTheme) Color(0xFFFBBF24) else Color(0xFFD97706)
    "pink" -> if (darkTheme) Color(0xFFF472B6) else Color(0xFFDB2777)
    "cyan" -> if (darkTheme) Color(0xFF22D3EE) else Color(0xFF0891B2)
    else -> if (darkTheme) CosmicDarkPrimary else CosmicLightPrimary
}

@Composable
fun NetcoreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disable dynamic colors to show custom branding
    accentStyle: String = "indigo",
    fontStyle: String = "default",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val accent = accentColor(accentStyle, darkTheme)
    val colorScheme = baseScheme.copy(
        primary = accent,
        primaryContainer = accent.copy(alpha = if (darkTheme) 0.22f else 0.12f),
        onPrimaryContainer = accent,
        secondary = accent
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = netcoreTypographyFor(fontStyle),
        shapes = NetcoreShapes,
        content = content
    )
}
