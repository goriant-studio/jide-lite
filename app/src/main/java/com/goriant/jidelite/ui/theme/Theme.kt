package com.goriant.jidelite.ui.theme

import android.app.Activity
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val JIdeLiteDarkColorScheme = darkColorScheme(
    primary = DarkPrimaryAccent,
    onPrimary = Color(0xFF0F141B),
    secondary = DarkSecondaryAccent,
    onSecondary = Color(0xFF1B130C),
    background = DarkBackground,
    onBackground = Color(0xFFF4F6F8),
    surface = DarkTopBarSurface,
    onSurface = Color(0xFFF4F6F8),
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHighest = DarkSelectedSurface,
    onSurfaceVariant = Color(0xFF98A0B2),
    outlineVariant = DarkOutlineSubtle
)

private val JIdeLiteLightColorScheme = lightColorScheme(
    primary = LightPrimaryAccent,
    onPrimary = Color(0xFFFCFBF8),
    secondary = LightSecondaryAccent,
    onSecondary = Color(0xFF2D1A05),
    background = LightBackground,
    onBackground = Color(0xFF182028),
    surface = LightTopBarSurface,
    onSurface = Color(0xFF1C2430),
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHighest = LightSelectedSurface,
    onSurfaceVariant = Color(0xFF6B7068),
    outlineVariant = LightOutlineSubtle
)

private val JIdeLiteShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JIdeLiteTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) JIdeLiteDarkColorScheme else JIdeLiteLightColorScheme
    val extraColors = if (darkTheme) DarkExtraColors else LightExtraColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            SideEffect {
                window.statusBarColor = extraColors.topBarSurface.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalJIdeLiteExtraColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            shapes = JIdeLiteShapes,
            content = content
        )
    }
}

private val LocalJIdeLiteExtraColors = staticCompositionLocalOf { DarkExtraColors }

val JIdeLiteColors: JIdeLiteExtraColors
    @Composable get() = LocalJIdeLiteExtraColors.current
