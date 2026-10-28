package com.example.medicat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.medicat.ui.theme.Cartoon
import kotlin.math.abs

enum class MedArtKind { BOTTLE, BOX, SYRUP, JAR, CAPSULE, TUBE, BLISTER, FIZZ }

/** Picks an illustration from the dosage form first, then the category. */
fun medArtFor(form: String, category: String): MedArtKind {
    when (form.trim().lowercase()) {
        "liquid", "drops" -> return MedArtKind.SYRUP
        "cream" -> return MedArtKind.TUBE
        "capsule" -> return MedArtKind.CAPSULE
    }
    return when (category.trim().lowercase()) {
        "vitamins" -> MedArtKind.JAR
        "allergy", "digestive" -> MedArtKind.BOX
        "pain relief", "prescription" -> MedArtKind.BOTTLE
        "inflammation" -> MedArtKind.TUBE
        "cold & flu" -> MedArtKind.SYRUP
        "skin care" -> MedArtKind.TUBE
        "first aid" -> MedArtKind.BLISTER
        else -> if (form.equals("tablet", ignoreCase = true)) MedArtKind.BLISTER
        else listOf(MedArtKind.BOTTLE, MedArtKind.BOX, MedArtKind.BLISTER)[abs(category.hashCode()) % 3]
    }
}

/** Soft tile colour behind a medication's art, per category. */
fun categoryTint(category: String): Color = when (category.trim().lowercase()) {
    "pain relief", "first aid" -> Cartoon.PinkSoft
    "inflammation" -> Cartoon.OrangeSoft
    "allergy", "prescription" -> Cartoon.BlueSoft
    "vitamins" -> Cartoon.YellowSoft
    "cold & flu", "digestive" -> Cartoon.BlueMist
    "skin care" -> Cartoon.Blush
    else -> listOf(Cartoon.BlueSoft, Cartoon.PinkSoft, Cartoon.YellowSoft, Cartoon.BlueMist)[abs(category.hashCode()) % 4]
}

/** Bold box colour per category, for the 3D models. */
fun categoryColor(category: String): Color = when (category.trim().lowercase()) {
    "pain relief", "first aid" -> Cartoon.Red
    "inflammation" -> Cartoon.Orange
    "allergy" -> Cartoon.Blue
    "vitamins" -> Cartoon.Yellow
    "cold & flu" -> Cartoon.BlueLight
    "digestive" -> Cartoon.BlueDeep
    "prescription" -> Cartoon.Coral
    "skin care" -> Cartoon.Pink
    else -> listOf(Cartoon.Blue, Cartoon.Red, Cartoon.Yellow, Cartoon.BlueDeep)[abs(category.hashCode()) % 4]
}

/** Cartoon medication illustration on a 64dp grid; scales to any [size]. */
@Composable
fun MedArt(kind: MedArtKind, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    Canvas(modifier.size(size)) {
        val unit = this.size.width / 64f
        scale(unit, unit, pivot = Offset.Zero) {
            when (kind) {
                MedArtKind.BOTTLE -> bottle()
                MedArtKind.BOX -> box()
                MedArtKind.SYRUP -> syrup()
                MedArtKind.JAR -> jar()
                MedArtKind.CAPSULE -> capsule()
                MedArtKind.TUBE -> tube()
                MedArtKind.BLISTER -> blister()
                MedArtKind.FIZZ -> fizz()
            }
        }
    }
}

/** Filled rounded rect with an ink border drawn inside its bounds, like CSS border-box. */
private fun DrawScope.inked(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Color, border: Float = 3f) {
    drawRoundRect(fill, Offset(x, y), Size(w, h), CornerRadius(r))
    if (border > 0f) {
        val half = border / 2
        drawRoundRect(
            Cartoon.Ink, Offset(x + half, y + half), Size(w - border, h - border),
            CornerRadius((r - half).coerceAtLeast(0f)), style = Stroke(border)
        )
    }
}

private fun DrawScope.inkedPath(rect: RoundRect, fill: Color, border: Float = 3f) {
    val path = Path().apply { addRoundRect(rect) }
    drawPath(path, fill)
    clipPath(path) { drawPath(path, Cartoon.Ink, style = Stroke(border * 2)) }
}

private fun DrawScope.bottle() {
    inked(12f, 15f, 40f, 44f, 9f, Cartoon.Red)
    inked(17f, 5f, 30f, 12f, 4f, Cartoon.Paper)
    drawRoundRect(Cartoon.Paper, Offset(20f, 30f), Size(24f, 14f), CornerRadius(3f))
}

private fun DrawScope.box() {
    rotate(-4f, Offset(32f, 32f)) {
        inked(9f, 5f, 46f, 54f, 6f, Cartoon.Blue)
        drawRoundRect(Cartoon.Paper, Offset(29f, 22f), Size(6f, 20f), CornerRadius(2f))
        drawRoundRect(Cartoon.Paper, Offset(22f, 29f), Size(20f, 6f), CornerRadius(2f))
    }
}

private fun DrawScope.syrup() {
    // neck: fill with ink sides only
    drawRect(Cartoon.BlueLight, Offset(24f, 14f), Size(16f, 8f))
    drawRect(Cartoon.Ink, Offset(24f, 14f), Size(3f, 8f))
    drawRect(Cartoon.Ink, Offset(37f, 14f), Size(3f, 8f))
    inked(10f, 21f, 44f, 40f, 12f, Cartoon.BlueLight)
    inked(21f, 3f, 22f, 12f, 4f, Cartoon.Red)
    drawRoundRect(Cartoon.Paper, Offset(19f, 33f), Size(26f, 16f), CornerRadius(4f))
    // drop
    val drop = Path().apply {
        moveTo(32f, 36.5f)
        cubicTo(34.5f, 39.5f, 36f, 41f, 36f, 42.5f)
        cubicTo(36f, 44.7f, 34.2f, 46.3f, 32f, 46.3f)
        cubicTo(29.8f, 46.3f, 28f, 44.7f, 28f, 42.5f)
        cubicTo(28f, 41f, 29.5f, 39.5f, 32f, 36.5f)
        close()
    }
    drawPath(drop, Cartoon.Red)
}

private fun DrawScope.jar() {
    // Body has no top border; the lid sits on it.
    val body = RoundRect(11f, 18f, 53f, 58f, CornerRadius.Zero, CornerRadius.Zero, CornerRadius(10f), CornerRadius(10f))
    inkedPath(body, Cartoon.Paper)
    drawRect(Cartoon.Yellow, Offset(14f, 33f), Size(36f, 10f))
    inked(8f, 6f, 48f, 14f, 5f, Cartoon.Blue)
}

private fun DrawScope.capsule() {
    rotate(-35f, Offset(32f, 32f)) {
        val x = 4f
        val y = 19f
        val outline = Path().apply { addRoundRect(RoundRect(x, y, x + 56f, y + 26f, CornerRadius(13f))) }
        clipPath(outline) {
            drawRect(Cartoon.Red, Offset(x, y), Size(28f, 26f))
            drawRect(Cartoon.Paper, Offset(x + 28f, y), Size(28f, 26f))
            drawRect(Cartoon.Ink, Offset(x + 26.5f, y), Size(3f, 26f))
            drawPath(outline, Cartoon.Ink, style = Stroke(6f))
        }
        // shine
        drawLine(Color.White.copy(alpha = 0.6f), Offset(x + 10f, y + 8f), Offset(x + 20f, y + 8f), 3f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

private fun DrawScope.tube() {
    rotate(-12f, Offset(32f, 32f)) {
        val body = RoundRect(4f, 21f, 48f, 43f, CornerRadius(4f), CornerRadius(10f), CornerRadius(10f), CornerRadius(4f))
        inkedPath(body, Cartoon.Paper)
        drawRoundRect(Cartoon.Blue, Offset(12f, 29.5f), Size(18f, 5f), CornerRadius(3f))
        // cap: no left border, rounded right
        val cap = RoundRect(48f, 24f, 60f, 40f, CornerRadius.Zero, CornerRadius(3f), CornerRadius(3f), CornerRadius.Zero)
        drawPath(Path().apply { addRoundRect(cap) }, Cartoon.Red)
        val edge = Path().apply {
            moveTo(48f, 25.5f); lineTo(57f, 25.5f)
            quadraticTo(58.5f, 25.5f, 58.5f, 27f)
            lineTo(58.5f, 37f)
            quadraticTo(58.5f, 38.5f, 57f, 38.5f)
            lineTo(48f, 38.5f)
        }
        drawPath(edge, Cartoon.Ink, style = Stroke(3f, join = StrokeJoin.Round))
    }
}

private fun DrawScope.blister() {
    inked(5f, 12f, 54f, 40f, 6f, Cartoon.Foil)
    val cellW = (54f - 6f - 8f - 6f) / 3f
    val cellH = (40f - 6f - 8f - 3f) / 2f
    for (row in 0..1) for (col in 0..2) {
        val cx = 5f + 3f + 4f + col * (cellW + 3f) + cellW / 2
        val cy = 12f + 3f + 4f + row * (cellH + 3f) + cellH / 2
        val r = minOf(cellW, cellH) / 2 - 1f
        if (row == 0 && col == 2) {
            drawCircle(Cartoon.FoilDash, r, Offset(cx, cy), style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 2.5f))))
        } else {
            drawCircle(Cartoon.Paper, r, Offset(cx, cy))
            drawCircle(Cartoon.Ink, r - 1f, Offset(cx, cy), style = Stroke(2f))
        }
    }
}

/** Tall effervescent-tablet tube with a chunky lid. */
private fun DrawScope.fizz() {
    inked(21f, 14f, 22f, 46f, 5f, Cartoon.Yellow)
    drawRect(Cartoon.Paper, Offset(24f, 28f), Size(16f, 16f))
    drawRect(Cartoon.Red, Offset(24f, 33f), Size(16f, 5f))
    inked(18f, 4f, 28f, 13f, 4f, Cartoon.Blue)
}

private val SquirclePath = "M50 0C86 0 100 14 100 50S86 100 50 100 0 86 0 50 14 0 50 0Z"
private val PillLeftPath = "M32 36H50V64H32A14 14 0 0 1 32 36Z"
private val PillRightPath = "M50 36H68A14 14 0 0 1 68 64H50Z"

/** The MediCab mark: a blue squircle holding a red and white capsule. */
@Composable
fun MediCabLogo(modifier: Modifier = Modifier, size: Dp = 52.dp, dropShadow: Boolean = false) {
    val paths = remember {
        listOf(SquirclePath, PillLeftPath, PillRightPath).map { PathParser().parsePathString(it).toPath() }
    }
    Canvas(modifier.size(size)) {
        // viewBox -4..104 on both axes
        val unit = this.size.width / 108f
        val stroke = (6f * 52.dp.toPx() / this.size.width).coerceIn(4f, 8f)
        scale(unit, unit, pivot = Offset.Zero) {
            translate(4f, 4f) {
                if (dropShadow) translate(top = 4f) { drawPath(paths[0], Cartoon.Ink) }
                drawPath(paths[0], Cartoon.Blue)
                drawPath(paths[0], Cartoon.Ink, style = Stroke(stroke))
                rotate(-45f, Offset(50f, 50f)) {
                    drawPath(paths[1], Cartoon.Red)
                    drawPath(paths[1], Cartoon.Ink, style = Stroke(stroke - 1f, join = StrokeJoin.Round))
                    drawPath(paths[2], Cartoon.Paper)
                    drawPath(paths[2], Cartoon.Ink, style = Stroke(stroke - 1f, join = StrokeJoin.Round))
                }
            }
        }
    }
}
