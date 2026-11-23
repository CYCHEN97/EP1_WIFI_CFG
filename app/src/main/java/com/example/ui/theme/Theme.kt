package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val IstLightColorScheme = lightColorScheme(
    primary = TechBlue,
    onPrimary = Color.White,
    primaryContainer = TechBlueContainer,
    onPrimaryContainer = TechBlue,
    secondary = MutedInk,
    onSecondary = Color.White,
    secondaryContainer = SoftSurfaceVariant,
    onSecondaryContainer = InkBlack,
    tertiary = ProvisionedGreen,
    onTertiary = Color.White,
    tertiaryContainer = ProvisionedGreenBg,
    onTertiaryContainer = ProvisionedGreen,
    background = WarmGrayBackground,
    onBackground = InkBlack,
    surface = PureWhiteSurface,
    onSurface = InkBlack,
    surfaceVariant = SoftSurfaceVariant,
    onSurfaceVariant = MutedInk,
    outline = SubtleBorder,
    outlineVariant = SubtleBorder,
    error = AlertRed,
    onError = Color.White,
    errorContainer = AlertRedBg,
    onErrorContainer = AlertRed
)

@Composable
fun IstNfcProvisionerTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = IstLightColorScheme,
        typography = Typography,
        content = content
    )
}
