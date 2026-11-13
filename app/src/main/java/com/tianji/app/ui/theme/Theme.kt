package com.tianji.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 墨（深色）：近黑底 + 青玉点缀
val InkBackground = Color(0xFF0C0D0F)
val InkSurface = Color(0xFF16181B)
val InkSurface2 = Color(0xFF1E2126)
val InkForeground = Color(0xFFE8EAED)
val InkMuted = Color(0xFF9BA1A6)
val InkPrimary = Color(0xFF8FC7CC)
val InkOnPrimary = Color(0xFF0C0D0F)
val InkSecondary = Color(0xFF3A3D42)
val InkOutline = Color(0xFF232529)
val InkError = Color(0xFFE5695E)

private val InkColors = darkColorScheme(
    primary = InkPrimary,
    onPrimary = InkOnPrimary,
    primaryContainer = Color(0xFF1F3E42),
    onPrimaryContainer = Color(0xFFC9E9EC),
    secondary = InkSecondary,
    onSecondary = InkForeground,
    secondaryContainer = Color(0xFF232B2C),
    onSecondaryContainer = Color(0xFFC9E9EC),
    tertiaryContainer = Color(0xFF232B2C),
    onTertiaryContainer = Color(0xFFC9E9EC),
    background = InkBackground,
    onBackground = InkForeground,
    surface = InkSurface,
    onSurface = InkForeground,
    surfaceVariant = InkSurface2,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color(0xFF070809),
    surfaceContainerLow = Color(0xFF131518),
    surfaceContainer = Color(0xFF17191C),
    surfaceContainerHigh = Color(0xFF1E2126),
    surfaceContainerHighest = Color(0xFF25282C),
    outline = InkOutline,
    outlineVariant = InkOutline,
    error = InkError,
    onError = InkOnPrimary,
    inverseSurface = Color(0xFFE8EAED),
    inverseOnSurface = Color(0xFF16181B),
    inversePrimary = Color(0xFF2E6F76),
)

// 纸（浅色）：暖白底 + 同一青玉色；容器色全部走青绿系，替代 Material 默认的紫色系
val PaperBackground = Color(0xFFF7F8F7)
val PaperSurface = Color(0xFFFFFFFF)
val PaperSurface2 = Color(0xFFEDF0F0)
val PaperForeground = Color(0xFF191C1E)
val PaperMuted = Color(0xFF596065)
val PaperPrimary = Color(0xFF2E6F76)
val PaperOnPrimary = Color(0xFFFFFFFF)
val PaperSecondary = Color(0xFFDCE4E4)
val PaperOutline = Color(0xFFD3D9DA)
val PaperError = Color(0xFFA93B2C)

private val PaperColors = lightColorScheme(
    primary = PaperPrimary,
    onPrimary = PaperOnPrimary,
    primaryContainer = Color(0xFFC4E4E7),
    onPrimaryContainer = Color(0xFF062F34),
    secondary = PaperSecondary,
    onSecondary = PaperForeground,
    secondaryContainer = Color(0xFFD8EAEA),
    onSecondaryContainer = Color(0xFF0E3236),
    tertiaryContainer = Color(0xFFD8EAEA),
    onTertiaryContainer = Color(0xFF0E3236),
    background = PaperBackground,
    onBackground = PaperForeground,
    surface = PaperSurface,
    onSurface = PaperForeground,
    surfaceVariant = PaperSurface2,
    onSurfaceVariant = PaperMuted,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F6F6),
    surfaceContainer = Color(0xFFEFF2F1),
    surfaceContainerHigh = Color(0xFFE9EDEC),
    surfaceContainerHighest = Color(0xFFE3E7E7),
    outline = PaperOutline,
    outlineVariant = PaperOutline,
    error = PaperError,
    onError = Color.White,
    inverseSurface = Color(0xFF2B2F30),
    inverseOnSurface = Color(0xFFF0F2F1),
    inversePrimary = Color(0xFF8FC7CC),
)

private val TianjiTypography = Typography().run {
    copy(
        headlineMedium = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            letterSpacing = 2.4.sp,
        ),
        titleLarge = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            letterSpacing = 0.8.sp,
        ),
        labelSmall = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        ),
    )
}

@Composable
fun TianjiTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) InkColors else PaperColors,
        typography = TianjiTypography,
        content = content,
    )
}
