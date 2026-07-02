package com.netcoremessenger.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val NetcoreTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)

fun netcoreTypographyFor(style: String): Typography {
    val family = when (style) {
        "minecraft" -> FontFamily.Monospace
        "mono" -> FontFamily.Monospace
        "serif" -> FontFamily.Serif
        "rounded" -> FontFamily.SansSerif
        "cursive" -> FontFamily.Cursive
        else -> FontFamily.Default
    }
    val titleWeight = if (style == "minecraft") FontWeight.Black else FontWeight.SemiBold

    return NetcoreTypography.copy(
        displayLarge = NetcoreTypography.displayLarge.copy(fontFamily = family, fontWeight = titleWeight),
        headlineLarge = NetcoreTypography.headlineLarge.copy(fontFamily = family, fontWeight = titleWeight),
        headlineMedium = NetcoreTypography.headlineMedium.copy(fontFamily = family, fontWeight = titleWeight),
        titleLarge = NetcoreTypography.titleLarge.copy(fontFamily = family),
        titleMedium = NetcoreTypography.titleMedium.copy(fontFamily = family),
        bodyLarge = NetcoreTypography.bodyLarge.copy(fontFamily = family),
        bodyMedium = NetcoreTypography.bodyMedium.copy(fontFamily = family),
        bodySmall = NetcoreTypography.bodySmall.copy(fontFamily = family),
        labelLarge = NetcoreTypography.labelLarge.copy(fontFamily = family),
        labelMedium = NetcoreTypography.labelMedium.copy(fontFamily = family),
        labelSmall = NetcoreTypography.labelSmall.copy(fontFamily = family)
    )
}
