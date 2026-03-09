package com.goriant.jidelite.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val JIdeLiteDarkColorScheme = darkColorScheme(
    primary = PrimaryAccent,
    onPrimary = Color(0xFF0F141B),
    secondary = SecondaryAccent,
    onSecondary = Color(0xFF1B130C),
    background = Background,
    onBackground = Color(0xFFF4F6F8),
    surface = TopBarSurface,
    onSurface = Color(0xFFF4F6F8),
    surfaceContainer = SurfaceContainer,
    surfaceContainerHighest = SurfaceContainer,
    onSurfaceVariant = Color(0xFF98A0B2),
    outlineVariant = OutlineSubtle
)

private val JIdeLiteShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JIdeLiteTheme(content: @Composable () -> Unit) {
    val colorScheme = JIdeLiteDarkColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        shapes = JIdeLiteShapes,
        content = content
    )
}
