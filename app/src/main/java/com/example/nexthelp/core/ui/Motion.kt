package com.example.nexthelp.core.ui

import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the user has disabled system animations ("remove animations" accessibility setting).
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }
}

private const val MEDIUM_DURATION = 220

data class AppMotion(
    val reduced: Boolean,
    val fadeDuration: Int = 160,
    val slideDistance: Int = 40
) {
    fun enterFade(): EnterTransition =
        if (reduced) EnterTransition.None else androidx.compose.animation.fadeIn(tween(fadeDuration))

    fun exitFade(): ExitTransition =
        if (reduced) ExitTransition.None else androidx.compose.animation.fadeOut(tween(fadeDuration))

    companion object {
        const val DURATION = MEDIUM_DURATION
    }
}
