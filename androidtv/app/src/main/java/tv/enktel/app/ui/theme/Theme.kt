package tv.enktel.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val EnktelBlue = Color(0xFF3B9DFF)
val EnktelBlueDeep = Color(0xFF1B6AE5)
val EnktelPurple = Color(0xFF8B5CF6)
val EnktelBg = Color(0xFF0A0E17)
val EnktelSurface = Color(0xFF121826)
val EnktelSurfaceHigh = Color(0xFF1B2333)
val EnktelText = Color(0xFFEAF0FA)
val EnktelTextDim = Color(0xFF93A0B8)
val EnktelLive = Color(0xFFEF4444)
val EnktelOk = Color(0xFF34D399)

@Composable
fun EnktelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = EnktelBlue,
            onPrimary = Color.White,
            secondary = EnktelPurple,
            onSecondary = Color.White,
            background = EnktelBg,
            onBackground = EnktelText,
            surface = EnktelSurface,
            onSurface = EnktelText,
            surfaceVariant = EnktelSurfaceHigh,
            onSurfaceVariant = EnktelTextDim,
            border = Color(0xFF2A3550),
        ),
        content = content,
    )
}
