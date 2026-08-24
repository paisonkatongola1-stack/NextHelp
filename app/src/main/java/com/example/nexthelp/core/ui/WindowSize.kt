package com.example.nexthelp.core.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

enum class WindowSizeClass { COMPACT, MEDIUM, EXPANDED }

object WindowBreakpoints {
    val Medium = 600.dp
    val Expanded = 840.dp
}

/**
 * Lightweight window size classification without extra dependencies.
 * COMPACT: phones in portrait. MEDIUM: large phones / small tablets.
 * EXPANDED: tablets, foldables open, desktops.
 */
@Composable
fun RememberWindowSizeClass(content: @Composable BoxWithConstraintsScope.(WindowSizeClass) -> Unit) {
    BoxWithConstraints {
        val widthDp = maxWidth
        val sizeClass = when {
            widthDp >= WindowBreakpoints.Expanded -> WindowSizeClass.EXPANDED
            widthDp >= WindowBreakpoints.Medium -> WindowSizeClass.MEDIUM
            else -> WindowSizeClass.COMPACT
        }
        content(sizeClass)
    }
}
