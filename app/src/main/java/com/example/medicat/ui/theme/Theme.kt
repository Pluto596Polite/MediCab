package com.example.medicat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.medicat.domain.ExpiryStatus

/**
 * Cartoon palette: bold blue and red on a pale blue ground, everything
 * outlined in near-black ink with a hard drop shadow.
 */
object Cartoon {
    val Ink = Color(0xFF111214)
    val InkMuted = Color(0xFF4A4F5C)
    val Placeholder = Color(0xFF6B7183)
    val Ground = Color(0xFFEEF3FF)
    val Paper = Color(0xFFFFFFFF)

    val Blue = Color(0xFF2F6BFF)
    val BlueDeep = Color(0xFF1D4ED8)
    val BlueSoft = Color(0xFFDDE7FF)
    val BlueMist = Color(0xFFE6EDFF)
    val BlueLight = Color(0xFF9DB8FF)

    val Red = Color(0xFFE01E37)
    val Coral = Color(0xFFFF5A6E)
    val Pink = Color(0xFFFF8FA0)
    val PinkLight = Color(0xFFFF9AA8)
    val Blush = Color(0xFFFFD3D9)
    val PinkSoft = Color(0xFFFFE1E5)

    val Orange = Color(0xFFFF8A3D)
    val OrangeSoft = Color(0xFFFFE6D3)

    val Yellow = Color(0xFFFFD166)
    val YellowSoft = Color(0xFFFFF3D6)

    val Grey = Color(0xFFD6DCEA)
    val Foil = Color(0xFFE4E9F4)
    val FoilDash = Color(0xFF8A9BC4)
}

private val CartoonColors = lightColorScheme(
    primary = Cartoon.Blue,
    onPrimary = Color.White,
    primaryContainer = Cartoon.BlueSoft,
    onPrimaryContainer = Cartoon.Ink,
    secondary = Cartoon.Red,
    onSecondary = Color.White,
    secondaryContainer = Cartoon.PinkSoft,
    onSecondaryContainer = Cartoon.Ink,
    tertiary = Cartoon.Yellow,
    onTertiary = Cartoon.Ink,
    background = Cartoon.Ground,
    onBackground = Cartoon.Ink,
    surface = Cartoon.Paper,
    onSurface = Cartoon.Ink,
    surfaceVariant = Cartoon.Ground,
    onSurfaceVariant = Cartoon.InkMuted,
    surfaceContainerLowest = Cartoon.Paper,
    surfaceContainerLow = Cartoon.Paper,
    surfaceContainer = Cartoon.Paper,
    surfaceContainerHigh = Cartoon.Paper,
    surfaceContainerHighest = Cartoon.Ground,
    outline = Cartoon.Ink,
    outlineVariant = Cartoon.Grey,
    error = Cartoon.Red,
    onError = Color.White,
    inverseSurface = Cartoon.Ink,
    inverseOnSurface = Color.White
)

/** Fill and text colour used for one expiry status. */
@Immutable
data class StatusTone(val fill: Color, val content: Color, val label: String)

fun ExpiryStatus.tone(): StatusTone = when (this) {
    ExpiryStatus.SAFE -> StatusTone(Cartoon.Blue, Color.White, "Fresh")
    ExpiryStatus.EXPIRING_SOON -> StatusTone(Cartoon.Pink, Cartoon.Ink, "Soon")
    ExpiryStatus.EXPIRED -> StatusTone(Cartoon.Red, Color.White, "Expired")
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** The cartoon design is light-only: the ink outlines are the point. */
@Composable
fun MediCabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CartoonColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
