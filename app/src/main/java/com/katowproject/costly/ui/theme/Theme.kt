package com.katowproject.costly.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Purple = Color(0xFF7437DB)
val Ink = Color(0xFF232035)
val Muted = Color(0xFF898499)
val Page = Color(0xFFFAF9FE)
val Lavender = Color(0xFFF0E8FD)
val Green = Color(0xFF18A96B)
val Red = Color(0xFFF15470)
val Line = Color(0xFFECE8F4)

@Composable
fun CostlyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Purple, onPrimary = Color.White,
            secondary = Purple, background = Page, surface = Color.White,
            onSurface = Ink, onBackground = Ink, outline = Line,
            surfaceVariant = Lavender, onSurfaceVariant = Muted, error = Red
        ),
        typography = Typography,
        content = content
    )
}
