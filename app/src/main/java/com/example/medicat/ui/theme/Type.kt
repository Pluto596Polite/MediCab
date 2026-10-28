package com.example.medicat.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.medicat.R

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) = Font(
    res,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

/** Round, chunky display face for headings, numbers and buttons. */
val Fredoka = FontFamily(
    variable(R.font.fredoka, 500),
    variable(R.font.fredoka, 600),
    variable(R.font.fredoka, 700)
)

/** Friendly rounded sans for body copy, used heavy. */
val Nunito = FontFamily(
    variable(R.font.nunito, 600),
    variable(R.font.nunito, 700),
    variable(R.font.nunito, 800)
)

private fun display(size: Int, line: Int, weight: FontWeight = FontWeight.Bold) = TextStyle(
    fontFamily = Fredoka,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp
)

private fun body(size: Int, line: Int, weight: FontWeight = FontWeight.Bold) = TextStyle(
    fontFamily = Nunito,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp
)

val AppTypography = Typography(
    displayLarge = display(80, 72),
    displayMedium = display(52, 56),
    displaySmall = display(44, 46),
    headlineLarge = display(34, 38),
    headlineMedium = display(28, 32),
    headlineSmall = display(24, 28),
    titleLarge = display(22, 26),
    titleMedium = display(17, 22, FontWeight.SemiBold),
    titleSmall = display(15, 20, FontWeight.SemiBold),
    bodyLarge = body(16, 22, FontWeight.ExtraBold),
    bodyMedium = body(15, 21),
    bodySmall = body(13, 18),
    labelLarge = display(16, 20, FontWeight.SemiBold),
    labelMedium = display(14, 18, FontWeight.SemiBold),
    labelSmall = display(12, 16, FontWeight.SemiBold).copy(letterSpacing = 0.05.em)
)
