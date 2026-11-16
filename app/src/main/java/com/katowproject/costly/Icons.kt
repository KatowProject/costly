package com.katowproject.costly

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private val iconPaths = mapOf(
    "home" to "M3 10L12 3L21 10V21H15V14H9V21H3Z",
    "list" to "M7 3H17A2 2 0 0 1 19 5V21L15 19L12 21L9 19L5 21V5A2 2 0 0 1 7 3ZM9 8H15M9 12H15",
    "plus" to "M12 5V19M5 12H19",
    "chart" to "M4 3V21H21M8 16V11M13 16V6M18 16V9",
    "menu" to "M4 6H20M4 12H20M4 18H20",
    "search" to "M21 21L16 16M18 10A8 8 0 1 1 2 10A8 8 0 0 1 18 10Z",
    "filter" to "M3 5H21M6 12H18M10 19H14",
    "arrow" to "M19 12H5M11 6L5 12L11 18",
    "chevron" to "M9 5L16 12L9 19",
    "down" to "M6 9L12 15L18 9",
    "trend" to "M4 17L10 11L14 14L21 5M14 5H21V12",
    "expense" to "M4 7L10 13L14 10L21 19M14 19H21V12",
    "wallet" to "M20 8V5A2 2 0 0 0 18 3H5A3 3 0 0 0 5 9H21V21H5A3 3 0 0 1 2 18V6M21 12H15V17H21M17 14.5H18",
    "food" to "M5 3V8A3 3 0 0 0 11 8V3M8 3V21M17 21V3C21 6 21 11 17 12",
    "car" to "M3 11L5 5H19L21 11V19H3ZM3 11H21M7 15H8M16 15H17M6 19V21M18 19V21",
    "bag" to "M3 7H21V21H3ZM8 7V3H16V7M3 12H21M10 12V15H14V12",
    "bolt" to "M13 2L4 14H11L10 22L20 9H13Z",
    "shopping" to "M5 7H19L21 21H3ZM8 8V5A4 4 0 0 1 16 5V8",
    "grid" to "M3 3H9V9H3ZM15 3H21V9H15ZM3 15H9V21H3ZM15 15H21V21H15Z",
    "target" to "M21 12A9 9 0 1 1 12 3M17 12A5 5 0 1 1 12 7M12 12L21 3M17 3H21V7",
    "cloud" to "M7 18H5A4 4 0 0 1 5 10A7 7 0 0 1 19 10A4 4 0 0 1 19 18H17M12 21V11M8 15L12 11L16 15",
    "shield" to "M12 2L21 6V12C21 17 17 20 12 22C7 20 3 17 3 12V6ZM8 12L11 15L17 9",
    "settings" to "M9 3H15L16 6L19 7L22 11L20 14V18L16 20L13 22L9 20H5L3 16L2 12L5 9V5ZM16 12A4 4 0 1 1 8 12A4 4 0 0 1 16 12Z",
    "repeat" to "M3 8H19L15 4M21 16H5L9 20M19 8L15 12M5 16L9 12",
    "people" to "M16 21V18A4 4 0 0 0 12 14H6A4 4 0 0 0 2 18V21M13 6A4 4 0 1 1 5 6A4 4 0 0 1 13 6ZM18 3A4 4 0 0 1 18 11M22 21V18A4 4 0 0 0 19 14",
    "file" to "M14 2H4V22H20V8ZM14 2V8H20M8 13H16M8 17H14",
    "eye" to "M2 12C7 3 17 3 22 12C17 21 7 21 2 12ZM15 12A3 3 0 1 1 9 12A3 3 0 0 1 15 12Z",
    "bell" to "M18 8A6 6 0 0 0 6 8V15L3 18H21L18 15ZM10 22H14",
    "calendar" to "M3 5H21V21H3ZM7 2V8M17 2V8M3 11H21M7 15H10M14 15H17",
    "check" to "M5 12L10 17L20 7",
    "close" to "M6 6L18 18M18 6L6 18",
    "trash" to "M3 6H21M9 6V3H15V6M5 6L6 21H18L19 6M10 10V17M14 10V17",
    "edit" to "M15 4L20 9M3 21L8 20L21 7L17 3L4 16Z",
    "camera" to "M3 7H7L9 3H15L17 7H21V21H3ZM16 13A4 4 0 1 1 8 13A4 4 0 0 1 16 13Z",
    "image" to "M3 3H21V21H3ZM3 17L9 11L14 16L17 13L21 17M9 7H9.1",
    "lock" to "M5 10H19V22H5ZM8 10V6A4 4 0 0 1 16 6V10M12 15V18",
    "download" to "M12 3V16M7 11L12 16L17 11M3 16V21H21V16",
    "sun" to "M16 12A4 4 0 1 1 8 12A4 4 0 0 1 16 12ZM12 1V4M12 20V23M1 12H4M20 12H23M4 4L6 6M18 18L20 20M4 20L6 18M18 6L20 4",
    "info" to "M21 12A9 9 0 1 1 3 12A9 9 0 0 1 21 12ZM12 11V17M12 7H12.1",
    "globe" to "M12 2A10 10 0 1 0 22 12A10 10 0 0 0 12 2ZM2 12H22M12 2A15 15 0 0 1 12 22A15 15 0 0 1 12 2Z"
)

@Composable
fun Glyph(name: String, modifier: Modifier = Modifier, tint: Color = Color.Unspecified, description: String? = null) {
    val vector = remember(name) {
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = PathParser().parsePathString(iconPaths[name] ?: iconPaths.getValue("grid")).toNodes(),
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ).build()
    }
    Icon(vector, description, modifier, tint)
}
