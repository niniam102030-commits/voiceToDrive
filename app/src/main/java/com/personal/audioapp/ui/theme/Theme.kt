package com.personal.audioapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF00897B),
    tertiary = Color(0xFF6A4FA3)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9EC7FF),
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF00497F),
    secondary = Color(0xFF4DD0C4)
)

/**
 * Theme of the app.
 *
 * The UI is Persian only, so the layout direction is forced to RTL regardless
 * of the phone's system language. All paddings/margins use symmetric or
 * `start`/`end` modifiers, which flip together with the direction.
 */
@Composable
fun AudioAppTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
            content = content
        )
    }
}
