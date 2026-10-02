package com.dojolog.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/** The app is dark-only; there is deliberately no light scheme. */
object DojoColors {
    val Background = Color(0xFF0E0F13)
    val SurfaceLowest = Color(0xFF0B0C0F)
    val SurfaceLow = Color(0xFF15171C)
    val Surface = Color(0xFF1A1D23)
    val SurfaceHigh = Color(0xFF22252D)
    val SurfaceHighest = Color(0xFF2A2E37)
    val Outline = Color(0xFF3B404C)
    val OutlineVariant = Color(0xFF2A2E37)

    val TextPrimary = Color(0xFFECEEF2)
    val TextSecondary = Color(0xFFA6ACB8)
    val TextMuted = Color(0xFF7D8390)

    val Primary = Color(0xFFFF6B63)
    val OnPrimary = Color(0xFF2E0807)
    val PrimaryContainer = Color(0xFF4A1D1C)
    val OnPrimaryContainer = Color(0xFFFFDAD6)

    /** Amber, used for stars and technique quality. */
    val Star = Color(0xFFF5B841)
    val StarEmpty = Color(0xFF4A4F5C)

    val Error = Color(0xFFFF8A80)

    // Charts: hue means a martial art, so charts across all arts use a light neutral (11.8:1
    // on the card), clearly apart from the grey of unnamed arts. Recessive chrome.
    val ChartSeries = Color(0xFFD5D8DF)
    val Grid = Color(0xFF262A32)
    val Baseline = Color(0xFF3A3F4B)
}

private val DarkScheme = darkColorScheme(
    primary = DojoColors.Primary,
    onPrimary = DojoColors.OnPrimary,
    primaryContainer = DojoColors.PrimaryContainer,
    onPrimaryContainer = DojoColors.OnPrimaryContainer,
    inversePrimary = Color(0xFFB3261E),
    secondary = DojoColors.Star,
    onSecondary = Color(0xFF2A1C00),
    // Selected chips and the navigation indicator use this: keep them in the accent family.
    secondaryContainer = Color(0xFF4A2221),
    onSecondaryContainer = Color(0xFFFFDAD6),
    tertiary = Color(0xFF6FB6FF),
    onTertiary = Color(0xFF00213F),
    tertiaryContainer = Color(0xFF173452),
    onTertiaryContainer = Color(0xFFD2E4FF),
    background = DojoColors.Background,
    onBackground = DojoColors.TextPrimary,
    surface = DojoColors.Background,
    onSurface = DojoColors.TextPrimary,
    surfaceVariant = DojoColors.SurfaceHigh,
    onSurfaceVariant = DojoColors.TextSecondary,
    surfaceTint = DojoColors.Primary,
    inverseSurface = DojoColors.TextPrimary,
    inverseOnSurface = DojoColors.Background,
    error = DojoColors.Error,
    onError = Color(0xFF3A0905),
    errorContainer = Color(0xFF5C1712),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = DojoColors.Outline,
    outlineVariant = DojoColors.OutlineVariant,
    scrim = Color.Black,
    surfaceBright = DojoColors.SurfaceHighest,
    surfaceDim = DojoColors.Background,
    surfaceContainerLowest = DojoColors.SurfaceLowest,
    surfaceContainerLow = DojoColors.SurfaceLow,
    surfaceContainer = DojoColors.Surface,
    surfaceContainerHigh = DojoColors.SurfaceHigh,
    surfaceContainerHighest = DojoColors.SurfaceHighest,
)

private val DojoTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun DojoLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = DojoTypography, content = content)
}
