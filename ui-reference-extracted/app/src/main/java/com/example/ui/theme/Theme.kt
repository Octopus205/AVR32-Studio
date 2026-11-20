package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val OctoDarkColorScheme = darkColorScheme(
    primary = OctoCyan,
    onPrimary = OctoAbyssNavy,
    primaryContainer = OctoCyanDim,
    onPrimaryContainer = OctoTextPrimary,
    secondary = OctoCobalt,
    onSecondary = OctoTextPrimary,
    secondaryContainer = OctoSurfaceCard,
    onSecondaryContainer = OctoTextPrimary,
    tertiary = OctoSuccess,
    background = OctoAbyssNavy,
    onBackground = OctoTextPrimary,
    surface = OctoSurfaceDark,
    onSurface = OctoTextPrimary,
    surfaceVariant = OctoSurfaceElevated,
    onSurfaceVariant = OctoTextSecondary,
    outline = OctoBorderSubtle,
    outlineVariant = OctoBorderSubtle,
    error = OctoError,
    onError = OctoTextPrimary
)

@Composable
fun OctoAVR32ATheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OctoDarkColorScheme,
        typography = OctoTypography,
        content = content
    )
}
