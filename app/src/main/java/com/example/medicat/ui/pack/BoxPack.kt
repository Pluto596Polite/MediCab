package com.example.medicat.ui.pack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medicat.ui.components.InkTag
import com.example.medicat.ui.components.MedArt
import com.example.medicat.ui.components.StatusTag
import com.example.medicat.ui.gallery.Box3D
import com.example.medicat.ui.gallery.BoxFace
import com.example.medicat.ui.theme.Cartoon
import com.example.medicat.ui.theme.tone

/** Size the box faces are designed at; real boxes are scaled from this by the projection. */
internal val BoxDesignWidth = 196.dp
internal val BoxDesignHeight = 260.dp
internal val BoxDesignDepth = 72.dp
private val Edge = 4.dp

/** Readable text colour on [fill]. */
internal fun onColor(fill: Color) = if (fill.luminance() > 0.5f) Cartoon.Ink else Color.White

/** A printed carton (or a slim blister-pack carton) in 3D. */
@Composable
internal fun BoxPack(
    spec: PackSpec,
    yaw: Float,
    pitch: Float,
    width: Dp,
    height: Dp,
    depth: Dp,
    modifier: Modifier = Modifier
) {
    val faces = remember(spec) {
        listOf(
            BoxFace { FrontFace(spec) },
            BoxFace { BackFace(spec) },
            BoxFace { SideFace(spec) },
            BoxFace { FlapFace(spec.color) }
        )
    }
    Box3D(
        width = width,
        height = height,
        depth = depth,
        yaw = yaw,
        pitch = pitch,
        designWidth = BoxDesignWidth,
        designHeight = BoxDesignHeight,
        designDepth = BoxDesignDepth,
        front = faces[0],
        back = faces[1],
        left = faces[2],
        right = faces[2],
        top = faces[3],
        bottom = faces[3],
        modifier = modifier
    )
}

/** Cardboard top/bottom with a tuck flap seam. */
@Composable
private fun FlapFace(color: Color) {
    Box(
        Modifier
            .fillMaxSize()
            .background(lerp(color, Color.White, 0.25f))
            .border(Edge, Cartoon.Ink)
    ) {
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(3.dp).background(Cartoon.Ink))
    }
}

@Composable
private fun FrontFace(spec: PackSpec) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Cartoon.Paper)
            .border(Edge, Cartoon.Ink, RoundedCornerShape(6.dp))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(spec.color)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MedArt(spec.template.art, size = 34.dp)
            Spacer(Modifier.width(4.dp))
            Text(
                spec.category.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = onColor(spec.color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(Modifier.fillMaxWidth().height(Edge).background(Cartoon.Ink))
        Column(Modifier.weight(1f).padding(16.dp)) {
            Text(
                spec.name.uppercase(),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, lineHeight = 23.sp),
                color = Cartoon.Ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (spec.detail.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(spec.detail, style = MaterialTheme.typography.bodySmall, color = Cartoon.InkMuted, maxLines = 2)
            }
            Spacer(Modifier.weight(1f))
            PackTags(spec)
        }
    }
}

/** Expiry and status tags printed on a pack. */
@Composable
internal fun PackTags(spec: PackSpec, spacing: Dp = 8.dp) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        spec.expiry?.let { InkTag(it, fill = Cartoon.Red, content = Color.White) }
        if (spec.status != null && spec.statusText != null) StatusTag(spec.status.tone(), text = spec.statusText)
    }
}

@Composable
private fun SideFace(spec: PackSpec) {
    Box(
        Modifier
            .fillMaxSize()
            .background(spec.color)
            .border(Edge, Cartoon.Ink),
        contentAlignment = Alignment.Center
    ) {
        Text(
            spec.name.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = onColor(spec.color),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.rotate(-90f).requiredWidth(230.dp)
        )
    }
}

@Composable
private fun BackFace(spec: PackSpec) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Cartoon.Ground)
            .border(Edge, Cartoon.Ink, RoundedCornerShape(6.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("DIRECTIONS", style = MaterialTheme.typography.labelSmall, color = Cartoon.InkMuted)
        // "Small print" lines.
        listOf(0.9f, 0.7f, 0.82f, 0.6f, 0.75f).forEach {
            Box(Modifier.fillMaxWidth(it).height(6.dp).background(Cartoon.BlueLight, RoundedCornerShape(3.dp)))
        }
        Spacer(Modifier.weight(1f))
        spec.barcode?.let { PrintedBarcode(it, Modifier.fillMaxWidth().height(46.dp)) }
    }
}

/** Decorative barcode whose bar widths come from the stored code. */
@Composable
internal fun PrintedBarcode(seed: String, modifier: Modifier) {
    Canvas(modifier.background(Color.White, RoundedCornerShape(2.dp)).padding(horizontal = 6.dp, vertical = 4.dp)) {
        val widths = seed.flatMap { c -> listOf(1 + c.code % 3, 1 + (c.code / 3) % 2) }.ifEmpty { listOf(1, 2) }
        val unit = size.width / (widths.sum() * 1.4f)
        var x = 0f
        var bar = true
        var i = 0
        while (x < size.width) {
            val bw = widths[i % widths.size] * unit
            if (bar) drawRect(Cartoon.Ink, topLeft = Offset(x, 0f), size = Size(bw, size.height))
            x += bw
            bar = !bar
            i++
        }
    }
}
