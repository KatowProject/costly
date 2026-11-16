package com.katowproject.costly.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class CostlyColors(
    val purple: Color,
    val ink: Color,
    val muted: Color,
    val page: Color,
    val card: Color,
    val lavender: Color,
    val green: Color,
    val red: Color,
    val line: Color,
    val isDark: Boolean
)

val LightCostlyColors = CostlyColors(
    purple = Color(0xFF7437DB),
    ink = Color(0xFF232035),
    muted = Color(0xFF898499),
    page = Color(0xFFFAF9FE),
    card = Color.White,
    lavender = Color(0xFFF0E8FD),
    green = Color(0xFF18A96B),
    red = Color(0xFFF15470),
    line = Color(0xFFECE8F4),
    isDark = false
)

val DarkCostlyColors = CostlyColors(
    purple = Color(0xFFA875FF),
    ink = Color(0xFFF5F4FA),
    muted = Color(0xFFA39EB1),
    page = Color(0xFF13111C),
    card = Color(0xFF1E1B29),
    lavender = Color(0xFF2C2442),
    green = Color(0xFF2FD68E),
    red = Color(0xFFFF6984),
    line = Color(0xFF2E293F),
    isDark = true
)

val LocalCostlyColors = staticCompositionLocalOf { LightCostlyColors }

val Purple: Color @Composable get() = LocalCostlyColors.current.purple
val Ink: Color @Composable get() = LocalCostlyColors.current.ink
val Muted: Color @Composable get() = LocalCostlyColors.current.muted
val Page: Color @Composable get() = LocalCostlyColors.current.page
val CardBg: Color @Composable get() = LocalCostlyColors.current.card
val Lavender: Color @Composable get() = LocalCostlyColors.current.lavender
val Green: Color @Composable get() = LocalCostlyColors.current.green
val Red: Color @Composable get() = LocalCostlyColors.current.red
val Line: Color @Composable get() = LocalCostlyColors.current.line

@Composable
fun CostlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkCostlyColors else LightCostlyColors
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.purple,
            onPrimary = Color(0xFF1B0B3B),
            secondary = colors.purple,
            background = colors.page,
            surface = colors.card,
            onSurface = colors.ink,
            onBackground = colors.ink,
            outline = colors.line,
            surfaceVariant = colors.lavender,
            onSurfaceVariant = colors.muted,
            error = colors.red
        )
    } else {
        lightColorScheme(
            primary = colors.purple,
            onPrimary = Color.White,
            secondary = colors.purple,
            background = colors.page,
            surface = colors.card,
            onSurface = colors.ink,
            onBackground = colors.ink,
            outline = colors.line,
            surfaceVariant = colors.lavender,
            onSurfaceVariant = colors.muted,
            error = colors.red
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalCostlyColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
