package com.realornot.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SageOlive,
    onPrimary = BgDark,
    primaryContainer = SageOlive,
    onPrimaryContainer = BgDark,
    secondary = SageLight,
    onSecondary = BgDark,
    tertiary = SageWarm,
    onTertiary = BgDark,
    background = BgDark,
    onBackground = TextWhite,
    surface = CardDark,
    onSurface = TextWhite,
    surfaceVariant = CardDarkLight,
    onSurfaceVariant = TextMuted,
    outline = SageDark,
    error = VerdictFake,
    onError = TextWhite,
)

@Composable
fun REALorNOTTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
