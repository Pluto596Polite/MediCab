package com.example.medicat.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Chunky round-stroke icons on a 24 grid, matching the cartoon design.
 * Icon() tints them, so the stroke colour here is irrelevant.
 */
object InkIcons {
    private fun icon(name: String, vararg paths: String, stroke: Float = 2.6f, fill: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = if (fill) SolidColor(Color.Black) else null,
                    stroke = if (fill) null else SolidColor(Color.Black),
                    strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round
                )
            }
        }.build()

    val Back = icon("back", "m15 18-6-6 6-6", stroke = 2.8f)
    val Forward = icon("forward", "m9 18 6-6-6-6", stroke = 2.8f)
    val Close = icon("close", "M18 6 6 18M6 6l12 12", stroke = 2.8f)
    val Bell = icon("bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0")
    val Flash = icon("flash", "M13 2 4 14h7l-1 8 9-12h-7l1-8Z")
    val Calendar = icon(
        "calendar",
        "M6 4h12a3 3 0 0 1 3 3v12a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3Z",
        "M16 2v4M8 2v4M3 10h18"
    )
    val Scan = icon(
        "scan",
        "M3 7V5a2 2 0 0 1 2-2h2M17 3h2a2 2 0 0 1 2 2v2M21 17v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2M7 12h10",
        stroke = 2.8f
    )
    val Cabinet = icon(
        "cabinet",
        "M6 3h12a3 3 0 0 1 3 3v12a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3Z",
        "M12 3v18M9 12h.01M15 12h.01"
    )
    val Profile = icon("profile", "M8 8a4 4 0 1 0 8 0a4 4 0 1 0-8 0", "M4 21a8 8 0 0 1 16 0")
    val Cube = icon(
        "cube",
        "M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z",
        "M3.3 7 12 12l8.7-5M12 22V12"
    )
    val Shelves = icon(
        "shelves",
        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z",
        "M3 9h18M3 15h18M7 6v0M12 12v0M16 18v0"
    )
    val More = icon(
        "more",
        "M2.8 12a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0-4.4 0Z",
        "M9.8 12a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0-4.4 0Z",
        "M16.8 12a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0-4.4 0Z",
        fill = true
    )
    val Minus = icon("minus", "M5 12h14", stroke = 3.2f)
    val Plus = icon("plus", "M12 5v14M5 12h14", stroke = 3.2f)
    val Check = icon("check", "M20 6 9 17l-5-5", stroke = 3.4f)
    val Camera = icon(
        "camera",
        "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3Z",
        "M9 13a3 3 0 1 0 6 0a3 3 0 1 0-6 0"
    )
    val Pencil = icon("pencil", "M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z")
    val Reset = icon("reset", "M3 12a9 9 0 1 0 3-6.7L3 8", "M3 3v5h5", stroke = 2.8f)
    val Spin = icon("spin", "M21 12a9 9 0 1 1-3-6.7L21 8", "M21 3v5h-5", stroke = 2.8f)
    val Search = icon("search", "M3 11a8 8 0 1 0 16 0a8 8 0 1 0-16 0", "m21 21-4.3-4.3", stroke = 2.8f)
    val Clock = icon("clock", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0", "M12 6v6l4 2")
    val Globe = icon(
        "globe",
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0",
        "M2 12h20M12 2a15 15 0 0 1 4 10 15 15 0 0 1-4 10 15 15 0 0 1-4-10 15 15 0 0 1 4-10Z"
    )
    val Lock = icon(
        "lock",
        "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2Z",
        "M7 11V7a5 5 0 0 1 10 0v4"
    )
    val Trash = icon(
        "trash",
        "M3 6h18",
        "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
        "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"
    )
    val Undo = icon("undo", "M3 7v6h6", "M21 17a9 9 0 0 0-9-9 9 9 0 0 0-6 2.3L3 13")
    val Barcode = icon("barcode", "M3 5v14M7 5v14M11 5v14M15 5v14M18 5v14M21 5v14")
    val Box = icon(
        "box",
        "M3 7 12 3l9 4v10l-9 4-9-4Z",
        "M3 7l9 4 9-4M12 11v10"
    )
}
