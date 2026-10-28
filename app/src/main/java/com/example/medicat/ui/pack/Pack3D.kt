package com.example.medicat.ui.pack

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.example.medicat.R
import com.example.medicat.ui.gallery.Box3D
import com.example.medicat.ui.gallery.BoxFace
import com.example.medicat.ui.theme.Cartoon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Label printed around a round pack: the front half and the back half. */
class PackTextures(val front: ImageBitmap, val back: ImageBitmap?)

/** How tall each template stands relative to the tallest, so a shelf looks natural. */
private val PackTemplate.heightShare: Float
    get() = when (this) {
        PackTemplate.CARTON -> 0.9f
        PackTemplate.BLISTER -> 0.9f
        PackTemplate.CREAM_TUBE -> 0.92f
        PackTemplate.PILL_BOTTLE -> 0.74f
        PackTemplate.FIZZ_TUBE -> 0.96f
        PackTemplate.SYRUP -> 1f
        PackTemplate.JAR -> 0.62f
    }

/**
 * A medication as a 3D object: its template printed with its details.
 *
 * Layout contract, so different templates line up on a shelf: the node is
 * [maxWidth] × ([maxHeight] + [floor]) and the centre of the pack's base always
 * sits at ([maxWidth] / 2, [maxHeight]). [floor] leaves room for the base
 * ellipse or box bottom seen from above.
 */
@Composable
fun Pack3D(
    spec: PackSpec,
    yaw: Float,
    pitch: Float,
    maxWidth: Dp,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    floor: Dp = maxWidth * 0.22f,
    naturalHeight: Boolean = true
) {
    val t = spec.template
    val height = if (naturalHeight) maxHeight * t.heightShare else maxHeight
    Box(modifier.size(maxWidth, maxHeight + floor)) {
        when (t.family) {
            PackFamily.BOX -> {
                var h = height
                var w = h * t.aspect
                var d = h * t.depth
                if (w + d > maxWidth) {
                    val s = maxWidth / (w + d)
                    h *= s; w *= s; d *= s
                }
                val cp = cos(Math.toRadians(pitch.toDouble())).toFloat()
                // Put the centre of the box's bottom face on the shared base point.
                val top = maxHeight - ((h + d) / 2 + h / 2 * cp)
                val boxModifier = Modifier.offset(x = (maxWidth - (w + d)) / 2, y = top)
                BoxPack(spec, yaw, pitch, w, h, d, boxModifier)
            }
            PackFamily.CYLINDER -> {
                val label = rememberGeneratedLabel(spec)
                Canvas(Modifier.fillMaxSize()) {
                    var total = height.toPx()
                    var radius = total * t.bodyShare * t.aspect / 2
                    val widest = radius * max(1f, t.lidRadius) * 2 + 4.dp.toPx()
                    if (widest > size.width) {
                        val s = size.width / widest
                        total *= s; radius *= s
                    }
                    drawRoundPack(t, label, spec.color, yaw, pitch, size.width / 2, maxHeight.toPx(), radius, total)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Round packs                                                          */
/* ------------------------------------------------------------------ */

/**
 * Draws a round pack: body, optional neck, lid. Base centre at ([cx], [baseY]).
 * Upright cylinders under a small tilt are drawn orthographically: a rim at
 * angle φ (0 = facing the viewer) lands at x = r·sin φ, y = y₀ + r·sin(pitch)·cos φ.
 */
private fun DrawScope.drawRoundPack(
    t: PackTemplate,
    tex: PackTextures?,
    color: Color,
    yaw: Float,
    pitch: Float,
    cx: Float,
    baseY: Float,
    radius: Float,
    total: Float
) {
    val p = Math.toRadians(pitch.toDouble())
    val sp = sin(p).toFloat()
    val cp = cos(p).toFloat()
    val ink = (total / 70f).coerceIn(1.5.dp.toPx(), 4.dp.toPx())
    val bodyH = total * t.bodyShare
    val neckH = if (t.neck) total * 0.08f else 0f
    val lidH = total - bodyH - neckH
    val lidColor = when (t) {
        PackTemplate.FIZZ_TUBE -> color
        PackTemplate.JAR -> lerp(color, Color.White, 0.15f)
        else -> Cartoon.Paper
    }
    val bodyColor = Cartoon.Paper

    val bodyTop = baseY - bodyH * cp
    val neckTop = bodyTop - neckH * cp
    val sections = buildList {
        add { drawSection(cx, baseY, radius, bodyH, sp, cp, yaw, ink, bodyColor, tex?.front, tex?.back, ridges = false) }
        if (t.neck) add { drawSection(cx, bodyTop, radius * 0.36f, neckH, sp, cp, yaw, ink, lerp(bodyColor, Color.Black, 0.1f), null, null, ridges = false) }
        add {
            drawSection(
                cx, neckTop, radius * t.lidRadius, lidH, sp, cp, yaw, ink, lidColor, null, null,
                ridges = t == PackTemplate.PILL_BOTTLE || t == PackTemplate.SYRUP
            )
        }
    }
    // Seen from above, higher parts overlap lower ones; from below, the reverse.
    (if (sp >= 0f) sections else sections.reversed()).forEach { it() }
}

private fun DrawScope.drawSection(
    cx: Float,
    yBase: Float,
    r: Float,
    h: Float,
    sp: Float,
    cp: Float,
    yaw: Float,
    ink: Float,
    fill: Color,
    front: ImageBitmap?,
    back: ImageBitmap?,
    ridges: Boolean
) {
    val yTop = yBase - h * cp
    val e = r * sp
    val ae = abs(e).coerceAtLeast(0.5f)
    val topOval = Rect(cx - r, yTop - ae, cx + r, yTop + ae)
    val baseOval = Rect(cx - r, yBase - ae, cx + r, yBase + ae)
    val silhouette = Path().apply {
        moveTo(cx - r, yTop)
        lineTo(cx - r, yBase)
        arcTo(baseOval, 180f, -180f, false)
        lineTo(cx + r, yTop)
        arcTo(topOval, 0f, -180f, false)
        close()
    }

    drawPath(silhouette, fill)
    if (front != null) {
        clipPath(silhouette) { drawWrappedLabel(cx, yTop, r, h * cp, e, yaw, front, back) }
    }
    // Roundness: a soft highlight on the left, shade on the right.
    clipPath(silhouette) {
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = 0.12f),
                0.22f to Color.White.copy(alpha = 0.28f),
                0.45f to Color.Transparent,
                0.8f to Color.Black.copy(alpha = 0.12f),
                1f to Color.Black.copy(alpha = 0.3f),
                startX = cx - r,
                endX = cx + r
            ),
            topLeft = Offset(cx - r, yTop - ae),
            size = Size(2 * r, yBase - yTop + 2 * ae)
        )
        if (ridges) {
            for (k in 0 until 24) {
                val phi = Math.toRadians((k * 15.0 - yaw))
                val c = cos(phi).toFloat()
                if (c <= 0.05f) continue
                val x = cx + r * sin(phi).toFloat()
                drawLine(Cartoon.Ink.copy(alpha = 0.35f), Offset(x, yTop + e * c), Offset(x, yBase + e * c), ink * 0.5f)
            }
        }
    }
    // End faces: the top is visible from above, the bottom from below.
    if (sp > 0.01f) {
        val oval = Path().apply { addOval(topOval) }
        drawPath(oval, lerp(fill, Color.White, 0.3f))
        drawPath(oval, Cartoon.Ink, style = Stroke(ink * 0.8f))
    } else if (sp < -0.01f) {
        val oval = Path().apply { addOval(baseOval) }
        drawPath(oval, lerp(fill, Color.Black, 0.25f))
        drawPath(oval, Cartoon.Ink, style = Stroke(ink * 0.8f))
    }
    drawPath(silhouette, Cartoon.Ink, style = Stroke(ink, join = StrokeJoin.Round))
}

/**
 * Wraps the label around the body. Each half is drawn as seen straight on;
 * sampling it at (sin θ + 1) / 2 makes it look flat when facing front and
 * curve away correctly as the pack turns.
 */
private fun DrawScope.drawWrappedLabel(
    cx: Float,
    yTop: Float,
    r: Float,
    height: Float,
    e: Float,
    yaw: Float,
    front: ImageBitmap,
    back: ImageBitmap?
) {
    // Each slice follows the rim's curve as a flat step; finer slices when tilted, where the curve is deeper.
    val step = if (abs(e) > 4.dp.toPx()) 4 else 10
    for (deg in -180 until 180 step step) {
        val mid = deg + step / 2f
        val inFront = mid > -90f && mid < 90f
        val image = if (inFront) front else back ?: continue
        val faceCentre = if (inFront) 0f else 180f
        val phi0 = Math.toRadians((deg - yaw).toDouble())
        val phi1 = Math.toRadians((deg + step - yaw).toDouble())
        val phiMid = Math.toRadians((mid - yaw).toDouble())
        if (cos(phiMid) <= 0.0) continue
        val x0 = cx + r * sin(phi0).toFloat()
        val x1 = cx + r * sin(phi1).toFloat()
        if (x1 - x0 < 0.3f) continue
        val s0 = ((sin(Math.toRadians((deg - faceCentre).toDouble())) + 1) / 2 * image.width).toInt()
        val s1 = ((sin(Math.toRadians((deg + step - faceCentre).toDouble())) + 1) / 2 * image.width).toInt()
        val srcX = minOf(s0, s1).coerceIn(0, image.width - 1)
        val srcW = abs(s1 - s0).coerceAtLeast(1).coerceAtMost(image.width - srcX)
        val y = yTop + e * cos(phiMid).toFloat()
        drawImage(
            image,
            srcOffset = IntOffset(srcX, 0),
            srcSize = IntSize(srcW, image.height),
            dstOffset = IntOffset(x0.toInt(), y.toInt()),
            dstSize = IntSize((x1 - x0 + 1.5f).toInt(), height.toInt().coerceAtLeast(1))
        )
    }
}

/* ------------------------------------------------------------------ */
/* Textures                                                             */
/* ------------------------------------------------------------------ */

private val labelCache = LruCache<String, PackTextures>(24)

/** The printed label for a round pack, made from its details. */
@Composable
private fun rememberGeneratedLabel(spec: PackSpec): PackTextures? {
    val context = LocalContext.current.applicationContext
    val key = listOf(spec.template, spec.name, spec.detail, spec.category, spec.expiry, spec.barcode).joinToString("|")
    val label by produceState(initialValue = labelCache.get(key), key) {
        value = labelCache.get(key) ?: withContext(Dispatchers.Default) {
            LabelPainter(context).paint(spec)
        }.also { labelCache.put(key, it) }
    }
    return label
}

/** Draws generated label textures with the app's fonts and colours. */
private class LabelPainter(context: Context) {
    private val fredoka: Typeface = ResourcesCompat.getFont(context, R.font.fredoka) ?: Typeface.DEFAULT_BOLD
    private val nunito: Typeface = ResourcesCompat.getFont(context, R.font.nunito) ?: Typeface.DEFAULT

    fun paint(spec: PackSpec): PackTextures {
        val h = 520
        val w = (h * spec.template.aspect).toInt().coerceIn(170, 640)
        val front = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(front).apply {
            drawColor(Cartoon.Paper.toArgb())
            val band = h * 0.16f
            drawRect(0f, 0f, w.toFloat(), band, fill(spec.color))
            drawRect(0f, band, w.toFloat(), band + 8f, fill(Cartoon.Ink))
            drawRect(0f, h - band * 0.55f, w.toFloat(), h.toFloat(), fill(spec.color))
            text(spec.category.uppercase(), w / 2f, band * 0.66f, band * 0.42f, fredoka, 600, onColor(spec.color), w * 0.9f)
            val narrow = w < h * 0.5f
            if (narrow) {
                // Tall, thin tubes print the name running up the side.
                save()
                rotate(-90f, w / 2f, h * 0.5f)
                text(spec.name.uppercase(), w / 2f, h * 0.5f + w * 0.12f, w * 0.34f, fredoka, 700, Cartoon.Ink, h * 0.6f)
                restore()
            } else {
                text(spec.name.uppercase(), w / 2f, h * 0.42f, w * 0.16f, fredoka, 700, Cartoon.Ink, w * 0.86f)
                if (spec.detail.isNotBlank()) text(spec.detail, w / 2f, h * 0.52f, w * 0.075f, nunito, 800, Cartoon.InkMuted, w * 0.86f)
            }
            spec.expiry?.let { exp ->
                val size = (if (narrow) w * 0.1f else w * 0.065f)
                val paint = textPaint(fredoka, 600, size, Color.White)
                val tw = paint.measureText(exp).coerceAtMost(w * 0.9f)
                val cy = h * 0.72f
                val rect = RectF(w / 2f - tw / 2 - 12, cy - size, w / 2f + tw / 2 + 12, cy + size * 0.5f)
                drawRoundRect(rect, 12f, 12f, fill(Cartoon.Red))
                drawRoundRect(rect, 12f, 12f, stroke(Cartoon.Ink, 5f))
                text(exp, w / 2f, cy + size * 0.12f, size, fredoka, 600, Color.White, w * 0.9f)
            }
        }
        val back = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(back).apply {
            drawColor(Cartoon.Ground.toArgb())
            val band = h * 0.16f
            drawRect(0f, 0f, w.toFloat(), band, fill(spec.color))
            drawRect(0f, band, w.toFloat(), band + 8f, fill(Cartoon.Ink))
            drawRect(0f, h - band * 0.55f, w.toFloat(), h.toFloat(), fill(spec.color))
            var y = band + 40f
            listOf(0.8f, 0.6f, 0.72f, 0.5f, 0.66f, 0.58f).forEach { f ->
                drawRoundRect(RectF(w * (1 - f) / 2, y, w * (1 + f) / 2, y + 12f), 6f, 6f, fill(Cartoon.BlueLight))
                y += 30f
            }
            // Barcode
            val seed = spec.barcode ?: spec.name
            val bw = w * 0.6f
            var x = w * 0.2f
            var i = 0
            val top = h * 0.66f
            while (x < w * 0.2f + bw) {
                val c = seed[i % seed.length].code
                val bar = (2 + c % 4) * 2f
                if (i % 2 == 0) drawRect(x, top, x + bar, top + h * 0.12f, fill(Cartoon.Ink))
                x += bar
                i++
            }
        }
        return PackTextures(front.asImageBitmap(), back.asImageBitmap())
    }

    private fun fill(color: Color) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() }
    private fun stroke(color: Color, width: Float) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb(); style = Paint.Style.STROKE; strokeWidth = width }

    private fun textPaint(face: Typeface, weight: Int, size: Float, color: Color) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        fontVariationSettings = "'wght' $weight"
        textSize = size
        this.color = color.toArgb()
        textAlign = Paint.Align.CENTER
    }

    /** Centred single-line text, shrunk to fit [maxWidth]. */
    private fun android.graphics.Canvas.text(
        value: String, x: Float, baseline: Float, size: Float, face: Typeface, weight: Int, color: Color, maxWidth: Float
    ) {
        val paint = textPaint(face, weight, size, color)
        val width = paint.measureText(value)
        if (width > maxWidth) paint.textSize = size * maxWidth / width
        drawText(value, x, baseline, paint)
    }
}
