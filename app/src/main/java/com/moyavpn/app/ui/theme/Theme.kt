package com.moyavpn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// MoyaVPN-Markenfarben
private val MoyaBlue = Color(0xFF2C6BED)
private val MoyaBlueDark = Color(0xFF1B49A8)
private val MoyaAccent = Color(0xFF3DDC97)

private val LightColors = lightColorScheme(
    primary = MoyaBlue,
    onPrimary = Color.White,
    secondary = MoyaAccent,
    background = Color(0xFFF5F7FB),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = MoyaBlue,
    onPrimary = Color.White,
    secondary = MoyaAccent,
    background = Color(0xFF0E1626),
    surface = Color(0xFF17223A),
)

@Composable
fun MoyaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
