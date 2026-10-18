package com.baijum.thaalam.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

// A warm, stage-friendly palette: deep charcoal, saffron for the downbeat and
// a cool teal for the answering pulse. Colours are never the only signal —
// every beat also carries a number and an accessibility description.
val Saffron = Color(0xFFF2B457)
val SaffronDeep = Color(0xFFC77A16)
val Teal = Color(0xFF5FD0C4)
val Ink = Color(0xFF0E1116)
val InkRaised = Color(0xFF151B23)
val InkSoft = Color(0xFF222B36)
val Parchment = Color(0xFFF6EFE3)
val Lavender = Color(0xFFB69CFF)
val Coral = Color(0xFFFF7A6E)

private val DarkColors = darkColorScheme(
    primary = Saffron,
    onPrimary = Color(0xFF2A1900),
    primaryContainer = Color(0xFF4A3208),
    onPrimaryContainer = Color(0xFFFFE2B0),
    secondary = Teal,
    onSecondary = Color(0xFF00201D),
    secondaryContainer = Color(0xFF123A36),
    onSecondaryContainer = Color(0xFFB8F5ED),
    tertiary = Lavender,
    onTertiary = Color(0xFF20143D),
    background = Ink,
    onBackground = Parchment,
    surface = InkRaised,
    onSurface = Parchment,
    surfaceVariant = InkSoft,
    onSurfaceVariant = Color(0xFFB8C2CE),
    outline = Color(0xFF536170),
    outlineVariant = Color(0xFF2F3A46),
    error = Coral,
    onError = Color(0xFF2B0500),
    inverseSurface = Parchment,
    inverseOnSurface = Ink,
    inversePrimary = SaffronDeep,
    surfaceDim = Color(0xFF0A0D11),
    surfaceBright = Color(0xFF2A3441),
    surfaceContainerLowest = Color(0xFF0A0D11),
    surfaceContainerLow = Color(0xFF121820),
    surfaceContainer = Color(0xFF171F28),
    surfaceContainerHigh = Color(0xFF1D2732),
    surfaceContainerHighest = Color(0xFF26313D),
    surfaceTint = Saffron,
    scrim = Color(0xFF000000),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF9A5B00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3B2),
    onPrimaryContainer = Color(0xFF2E1A00),
    secondary = Color(0xFF006B62),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB6F2EA),
    onSecondaryContainer = Color(0xFF00201D),
    tertiary = Color(0xFF5C3DBB),
    onTertiary = Color.White,
    background = Color(0xFFFBF7F0),
    onBackground = Color(0xFF1D1B16),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1B16),
    surfaceVariant = Color(0xFFF0E9DD),
    onSurfaceVariant = Color(0xFF4C463C),
    outline = Color(0xFF807568),
    outlineVariant = Color(0xFFD8D0C2),
    error = Color(0xFFB3261E),
    surfaceDim = Color(0xFFE3DCCF),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF5EC),
    surfaceContainer = Color(0xFFF3EDE2),
    surfaceContainerHigh = Color(0xFFEDE6DA),
    surfaceContainerHighest = Color(0xFFE7DFD2),
    surfaceTint = Color(0xFF9A5B00),
    scrim = Color(0xFF000000),
)

private val ThaaLamTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 88.sp,
        lineHeight = 92.sp,
        letterSpacing = (-3).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 54.sp,
        letterSpacing = (-1).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.4.sp,
    ),
)

private val ThaaLamShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun ThaaLamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ThaaLamTypography,
        shapes = ThaaLamShapes,
        content = content,
    )
}
