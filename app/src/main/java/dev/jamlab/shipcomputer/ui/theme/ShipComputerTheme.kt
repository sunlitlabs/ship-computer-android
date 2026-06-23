package dev.jamlab.shipcomputer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFF00E5FF),
    background = Color(0xFF0A0A0F),
    surface = Color(0xFF12121A),
    onPrimary = Color(0xFF0A0A0F),
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun ShipComputerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
