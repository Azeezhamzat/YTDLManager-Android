package io.github.azeezhamzat.ytdlmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The desktop app's palette (ui/theme.py): accent #2f6fed, notice blues.
private val Light = lightColorScheme(
    primary = Color(0xFF2F6FED),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6EEFF),
    onPrimaryContainer = Color(0xFF132A63),
    secondaryContainer = Color(0xFFEEF3FF),
    onSecondaryContainer = Color(0xFF2A3D6B),
    background = Color(0xFFF3F4F7),
    surface = Color(0xFFF3F4F7),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    error = Color(0xFFC0392B),
    errorContainer = Color(0xFFFDECEA),
    onErrorContainer = Color(0xFF7A1F16),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FB0FF),
    onPrimary = Color(0xFF0B2A6B),
    primaryContainer = Color(0xFF1D48A3),
    onPrimaryContainer = Color(0xFFE6EEFF),
    secondaryContainer = Color(0xFF24314F),
    onSecondaryContainer = Color(0xFFD8E2FF),
    error = Color(0xFFFFB4A9),
    errorContainer = Color(0xFF5C1A12),
    onErrorContainer = Color(0xFFFFDAD4),
)

@Composable
fun YtdlTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
