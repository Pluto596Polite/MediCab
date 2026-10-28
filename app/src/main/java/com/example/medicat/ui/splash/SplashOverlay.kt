package com.example.medicat.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.medicat.ui.components.DecorCircle
import com.example.medicat.ui.components.LightStatusBarIcons
import com.example.medicat.ui.components.MedArt
import com.example.medicat.ui.components.MedArtKind
import com.example.medicat.ui.components.inkTextShadow
import com.example.medicat.ui.theme.Cartoon

/** Total length of the splash timeline. */
private const val DurationMillis = 4200

/** Point in the timeline (0..1) where the home screen is fully revealed. */
const val SplashRevealAt = 0.8f

private val Pop = CubicBezierEasing(0.3f, 1.6f, 0.5f, 1f)
private val Door = CubicBezierEasing(0.6f, 0f, 0.3f, 1.2f)
private val Smooth = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

/** Progress of a sub-animation running from [start] to [end] of the timeline. */
private fun Float.between(start: Float, end: Float, easing: Easing = LinearEasing): Float =
    easing.transform(((this - start) / (end - start)).coerceIn(0f, 1f))

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * Plays the launch animation over the home screen: a cartoon cabinet pops in,
 * its door swings open, a pill box floats out and spins, goes back in, the door
 * shuts and the splash fades away. Tap to skip.
 * [onProgress] receives the timeline position so the home screen can settle in.
 */
@Composable
fun SplashOverlay(onProgress: (Float) -> Unit, onFinished: () -> Unit) {
    val timeline = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        timeline.animateTo(1f, tween(DurationMillis, easing = LinearEasing)) { onProgress(value) }
        onFinished()
    }
    LightStatusBarIcons()
    val t = timeline.value

    val fade = 1f - t.between(0.667f, 0.773f, FastOutSlowInEasing)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade }
            .background(Cartoon.Blue)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFinished() }
            .semantics { contentDescription = "MediCab. A cabinet door opens, a pill box spins out and back in. Tap to skip." }
    ) {
        DecorCircle(Modifier.offset((-180).dp, (-140).dp).size(420.dp), ring = 60.dp, alpha = 0.08f)
        DecorCircle(Modifier.align(Alignment.BottomEnd).offset(110.dp, (-120).dp).size(260.dp), alpha = 0.07f)

        Column(
            Modifier.align(Alignment.Center).padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(70.dp)
        ) {
            Box(contentAlignment = Alignment.TopCenter) {
                // Floor shadow
                Box(
                    Modifier
                        .padding(top = 320.dp)
                        .size(width = 220.dp, height = 24.dp)
                        .background(Cartoon.BlueDeep, CircleShape)
                )
                Cabinet(t)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "MediCab",
                    style = MaterialTheme.typography.displayMedium.copy(shadow = inkTextShadow(4.dp)),
                    color = Color.White
                )
                Text("Your cabinet, sorted.", style = MaterialTheme.typography.bodyLarge, color = Color.White)
            }
        }
    }
}

@Composable
private fun Cabinet(t: Float) {
    val pop = t.between(0f, 0.08f, Pop)
    val door = t.between(0.107f, 0.213f, Door) - t.between(0.587f, 0.667f, Door)
    val rise = t.between(0.213f, 0.32f, Smooth) - t.between(0.48f, 0.587f, Smooth)
    val bob = t.between(0.32f, 0.40f, Smooth) - t.between(0.40f, 0.48f, Smooth)
    val spin = t.between(0.30f, 0.50f, Smooth) * 1440f
    val ringIn = t.between(0.32f, 0.38f)
    val ringOut = t.between(0.44f, 0.50f)

    Box(
        Modifier
            .size(width = 250.dp, height = 332.dp)
            .graphicsLayer {
                val s = lerp(0.6f, 1f, pop)
                scaleX = s
                scaleY = s
                alpha = pop.coerceIn(0f, 1f)
            }
    ) {
        // Feet
        Foot(Modifier.align(Alignment.BottomStart).padding(start = 30.dp))
        Foot(Modifier.align(Alignment.BottomEnd).padding(end = 30.dp))

        // Body
        Box(
            Modifier
                .size(250.dp, 320.dp)
                .background(Cartoon.Paper, RoundedCornerShape(22.dp))
                .border(6.dp, Cartoon.Ink, RoundedCornerShape(22.dp))
        ) {
            // Cavity with three shelves of medication
            Box(
                Modifier
                    .offset(16.dp, 16.dp)
                    .size(218.dp, 288.dp)
                    .background(Cartoon.BlueSoft, RoundedCornerShape(12.dp))
                    .border(4.dp, Cartoon.Ink, RoundedCornerShape(12.dp))
            ) {
                Shelf(94.dp)
                Shelf(186.dp)
                Item(MedArtKind.BOTTLE, x = 6.dp, baseline = 94.dp)
                Item(MedArtKind.BOX, x = 152.dp, baseline = 94.dp)
                Item(MedArtKind.SYRUP, x = 6.dp, baseline = 186.dp)
                Item(MedArtKind.TUBE, x = 80.dp, baseline = 186.dp)
                Item(MedArtKind.CAPSULE, x = 150.dp, baseline = 186.dp)
                Item(MedArtKind.BLISTER, x = 6.dp, baseline = 280.dp)
                Item(MedArtKind.JAR, x = 80.dp, baseline = 280.dp)
                Item(MedArtKind.BOTTLE, x = 150.dp, baseline = 280.dp)

                // The hero pill box that floats out and spins.
                Box(
                    Modifier
                        .offset(74.dp, 94.dp - 46.dp - 2.dp)
                        .size(70.dp, 46.dp)
                        .graphicsLayer {
                            translationY = -(rise * 170f + bob * 16f) * density
                            val s = 1f + 0.6f * rise
                            scaleX = s
                            scaleY = s
                        }
                ) {
                    // Dashed sparkle ring
                    Canvas(
                        Modifier
                            .offset((-22).dp, (-34).dp)
                            .size(114.dp)
                            .graphicsLayer {
                                alpha = ringIn - ringOut
                                val s = lerp(0.6f, 1f, ringIn) + 0.25f * ringOut
                                scaleX = s
                                scaleY = s
                            }
                    ) {
                        drawCircle(
                            Color.White,
                            style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f)))
                        )
                    }
                    PillBox(Modifier.graphicsLayer { rotationZ = spin })
                }
            }

            // Door, hinged on the left
            Box(
                Modifier
                    .offset(4.dp, 4.dp)
                    .size(242.dp, 312.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        rotationY = -118f * door
                        cameraDistance = 14f * density
                    }
                    .background(Cartoon.Paper, RoundedCornerShape(16.dp))
                    .border(6.dp, Cartoon.Ink, RoundedCornerShape(16.dp))
            ) {
                // Glassy highlights
                Box(
                    Modifier
                        .offset(24.dp, 30.dp)
                        .size(14.dp, 150.dp)
                        .graphicsLayer { rotationZ = 12f }
                        .background(Cartoon.BlueMist, RoundedCornerShape(7.dp))
                )
                Box(
                    Modifier
                        .offset(46.dp, 30.dp)
                        .size(6.dp, 110.dp)
                        .graphicsLayer { rotationZ = 12f }
                        .background(Cartoon.BlueMist, RoundedCornerShape(3.dp))
                )
                // Blue cross
                Box(Modifier.align(Alignment.Center).size(20.dp, 56.dp).background(Cartoon.Blue, RoundedCornerShape(5.dp)))
                Box(Modifier.align(Alignment.Center).size(56.dp, 20.dp).background(Cartoon.Blue, RoundedCornerShape(5.dp)))
                // Handle
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 14.dp)
                        .size(14.dp, 28.dp)
                        .background(Cartoon.Ink, RoundedCornerShape(7.dp))
                )
            }
        }
    }
}

@Composable
private fun Foot(modifier: Modifier) {
    Box(modifier.size(26.dp, 14.dp).background(Cartoon.Ink, RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)))
}

@Composable
private fun Shelf(top: Dp) {
    Box(Modifier.offset(0.dp, top).size(218.dp, 8.dp).background(Cartoon.Ink))
}

@Composable
private fun Item(kind: MedArtKind, x: Dp, baseline: Dp, size: Dp = 60.dp) {
    MedArt(kind, Modifier.offset(x, baseline - size + 2.dp), size = size)
}

/** White pill box with a red lid band and a capsule printed on it. */
@Composable
private fun PillBox(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(70.dp, 46.dp)
            .background(Cartoon.Paper, RoundedCornerShape(8.dp))
            .border(4.dp, Cartoon.Ink, RoundedCornerShape(8.dp))
    ) {
        Box(
            Modifier
                .size(70.dp, 13.dp)
                .background(Cartoon.Red, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .border(4.dp, Cartoon.Ink, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
        )
        MedArt(MedArtKind.CAPSULE, Modifier.align(Alignment.BottomCenter).offset(y = 2.dp), size = 34.dp)
    }
}
