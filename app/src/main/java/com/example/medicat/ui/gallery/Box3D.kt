package com.example.medicat.ui.gallery

import android.graphics.Matrix
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** One face of a [Box3D]; [content] is laid out at the face's design size. */
class BoxFace(val content: @Composable () -> Unit)

/**
 * A cuboid drawn with real perspective. The 8 corners are rotated by [yaw]
 * (positive turns the right side towards the viewer) and [pitch] (positive
 * tilts the top towards the viewer), projected, and each face's composable
 * content is mapped onto its projected quad with a perspective matrix.
 * Back-facing faces are skipped; for a convex box the rest never overlap,
 * so no depth sorting is needed.
 *
 * Faces are composed at the design size ([designWidth] etc.) and scaled to the
 * real size by the same matrix, so one face layout serves every box size.
 */
@Composable
fun Box3D(
    width: Dp,
    height: Dp,
    depth: Dp,
    yaw: Float,
    pitch: Float,
    front: BoxFace,
    back: BoxFace,
    left: BoxFace,
    right: BoxFace,
    top: BoxFace,
    bottom: BoxFace,
    modifier: Modifier = Modifier,
    designWidth: Dp = width,
    designHeight: Dp = height,
    designDepth: Dp = depth
) {
    val density = LocalDensity.current
    val w = with(density) { width.toPx() }
    val h = with(density) { height.toPx() }
    val d = with(density) { depth.toPx() }
    val projection = remember3D(w, h, d, yaw, pitch)

    // The container leaves room for the sides and top/bottom to show.
    Box(modifier.size(width + depth, height + depth), contentAlignment = Alignment.TopStart) {
        listOf(
            Triple(Side.FRONT, front, Pair(designWidth, designHeight)),
            Triple(Side.BACK, back, Pair(designWidth, designHeight)),
            Triple(Side.LEFT, left, Pair(designDepth, designHeight)),
            Triple(Side.RIGHT, right, Pair(designDepth, designHeight)),
            Triple(Side.TOP, top, Pair(designWidth, designDepth)),
            Triple(Side.BOTTOM, bottom, Pair(designWidth, designDepth))
        ).forEach { (side, face, size) ->
            val quad = projection.quads.getValue(side)
            if (!quad.visible) return@forEach
            Box(
                Modifier
                    // Lay the face out at its design size but pin its origin to the
                    // container's top-left; the matrix below does all positioning.
                    .layout { measurable, _ ->
                        val placeable = measurable.measure(
                            Constraints.fixed(size.first.roundToPx(), size.second.roundToPx())
                        )
                        layout(0, 0) { placeable.place(0, 0) }
                    }
                    .drawWithContent {
                        val src = floatArrayOf(0f, 0f, this.size.width, 0f, this.size.width, this.size.height, 0f, this.size.height)
                        val matrix = Matrix().apply { setPolyToPoly(src, 0, quad.points, 0, 4) }
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.save()
                            canvas.nativeCanvas.concat(matrix)
                            drawContent()
                            if (quad.shade > 0.01f) drawRect(Color.Black.copy(alpha = quad.shade))
                            canvas.nativeCanvas.restore()
                        }
                    }
            ) { face.content() }
        }
    }
}

private enum class Side { FRONT, BACK, LEFT, RIGHT, TOP, BOTTOM }

private class Quad(val points: FloatArray, val visible: Boolean, val shade: Float)

private class Projection(val quads: Map<Side, Quad>)

private fun remember3D(w: Float, h: Float, d: Float, yaw: Float, pitch: Float): Projection {
    val yr = Math.toRadians(yaw.toDouble())
    val pr = Math.toRadians(pitch.toDouble())
    val cy = cos(yr).toFloat()
    val sy = sin(yr).toFloat()
    val cp = cos(pr).toFloat()
    val sp = sin(pr).toFloat()
    val focal = 4.2f * max(w, h)
    val containerW = w + d
    val containerH = h + d
    val centreX = containerW / 2
    val centreY = containerH / 2

    // x right, y down, z towards the viewer.
    fun rotate(x: Float, y: Float, z: Float): FloatArray {
        val x1 = x * cy - z * sy
        val z1 = x * sy + z * cy
        val y2 = y * cp + z1 * sp
        val z2 = z1 * cp - y * sp
        return floatArrayOf(x1, y2, z2)
    }

    fun project(x: Float, y: Float, z: Float): Pair<Float, Float> {
        val (rx, ry, rz) = rotate(x, y, z).let { Triple(it[0], it[1], it[2]) }
        val scale = focal / (focal - rz)
        return Pair(centreX + rx * scale, centreY + ry * scale)
    }

    val hw = w / 2
    val hh = h / 2
    val hd = d / 2
    // Corners of each face in reading order (TL, TR, BR, BL) as seen from outside, plus the outward normal.
    val faces = mapOf(
        Side.FRONT to (listOf(
            floatArrayOf(-hw, -hh, hd), floatArrayOf(hw, -hh, hd), floatArrayOf(hw, hh, hd), floatArrayOf(-hw, hh, hd)
        ) to floatArrayOf(0f, 0f, 1f)),
        Side.BACK to (listOf(
            floatArrayOf(hw, -hh, -hd), floatArrayOf(-hw, -hh, -hd), floatArrayOf(-hw, hh, -hd), floatArrayOf(hw, hh, -hd)
        ) to floatArrayOf(0f, 0f, -1f)),
        Side.RIGHT to (listOf(
            floatArrayOf(hw, -hh, hd), floatArrayOf(hw, -hh, -hd), floatArrayOf(hw, hh, -hd), floatArrayOf(hw, hh, hd)
        ) to floatArrayOf(1f, 0f, 0f)),
        Side.LEFT to (listOf(
            floatArrayOf(-hw, -hh, -hd), floatArrayOf(-hw, -hh, hd), floatArrayOf(-hw, hh, hd), floatArrayOf(-hw, hh, -hd)
        ) to floatArrayOf(-1f, 0f, 0f)),
        Side.TOP to (listOf(
            floatArrayOf(-hw, -hh, -hd), floatArrayOf(hw, -hh, -hd), floatArrayOf(hw, -hh, hd), floatArrayOf(-hw, -hh, hd)
        ) to floatArrayOf(0f, -1f, 0f)),
        Side.BOTTOM to (listOf(
            floatArrayOf(-hw, hh, hd), floatArrayOf(hw, hh, hd), floatArrayOf(hw, hh, -hd), floatArrayOf(-hw, hh, -hd)
        ) to floatArrayOf(0f, 1f, 0f))
    )

    // Light from the upper left, slightly in front.
    val light = floatArrayOf(-0.35f, -0.55f, 0.76f).let { v ->
        val len = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); floatArrayOf(v[0] / len, v[1] / len, v[2] / len)
    }

    val quads = faces.mapValues { (_, face) ->
        val (corners, normal) = face
        val points = FloatArray(8)
        corners.forEachIndexed { i, c ->
            val (px, py) = project(c[0], c[1], c[2])
            points[i * 2] = px
            points[i * 2 + 1] = py
        }
        // Shoelace area: positive means the face is wound clockwise on screen, i.e. faces us.
        var area = 0f
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            area += points[i * 2] * points[j * 2 + 1] - points[j * 2] * points[i * 2 + 1]
        }
        val n = rotate(normal[0], normal[1], normal[2])
        val lambert = (n[0] * light[0] + n[1] * light[1] + n[2] * light[2]).coerceIn(0f, 1f)
        // Faces lit about as well as a front-on face stay clean; others darken.
        Quad(expand(points, 0.6f), visible = area > 1f, shade = ((0.8f - lambert) * 0.6f).coerceIn(0f, 0.5f))
    }
    return Projection(quads)
}

/** Grows a quad slightly so neighbouring faces overlap instead of showing hairline seams. */
private fun expand(points: FloatArray, by: Float): FloatArray {
    val cx = (points[0] + points[2] + points[4] + points[6]) / 4
    val cy = (points[1] + points[3] + points[5] + points[7]) / 4
    return FloatArray(8) { i ->
        val c = if (i % 2 == 0) cx else cy
        val v = points[i] - c
        val len = if (i % 2 == 0) {
            sqrt(v * v + (points[i + 1] - cy).let { it * it })
        } else {
            sqrt(v * v + (points[i - 1] - cx).let { it * it })
        }
        if (len == 0f) points[i] else points[i] + v / len * by
    }
}
