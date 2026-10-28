package com.example.medicat.ui.components

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.medicat.ui.theme.Cartoon
import com.example.medicat.ui.theme.Fredoka
import com.example.medicat.ui.theme.StatusTone
import kotlinx.coroutines.delay

/** Width of every ink outline. */
val InkWidth = 3.dp

/**
 * The signature cartoon surface: a filled shape with an ink outline and a hard
 * ink shadow underneath. When [onClick] is set the surface sinks onto its
 * shadow while pressed, like a physical button.
 */
@Composable
fun Modifier.cartoon(
    color: Color,
    shape: Shape,
    shadow: Dp = 4.dp,
    border: Dp = InkWidth,
    borderColor: Color = Cartoon.Ink,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed && onClick != null && enabled) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 1800f),
        label = "press"
    )
    return this
        .graphicsLayer { translationY = press * shadow.toPx() }
        .drawBehind {
            val drop = shadow.toPx() * (1f - press)
            if (drop > 0.5f) {
                translate(top = drop) { drawOutline(shape.createOutline(size, layoutDirection, this), Cartoon.Ink) }
            }
        }
        .clip(shape)
        .background(color)
        .then(if (border > 0.dp) Modifier.border(border, borderColor, shape) else Modifier)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interaction,
                    indication = ripple(color = Cartoon.Ink),
                    enabled = enabled,
                    role = role,
                    onClickLabel = onClickLabel,
                    onClick = onClick
                )
            } else Modifier
        )
}

/** White card with ink outline and drop shadow. */
@Composable
fun CartoonCard(
    modifier: Modifier = Modifier,
    color: Color = Cartoon.Paper,
    shape: Shape = RoundedCornerShape(20.dp),
    shadow: Dp = 4.dp,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .cartoon(color, shape, shadow, onClick = onClick)
            .padding(contentPadding),
        content = content
    )
}

/** Big rounded action button. */
@Composable
fun CartoonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Cartoon.Blue,
    contentColor: Color = Color.White,
    icon: ImageVector? = null,
    trailingIcon: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 58.dp,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize)
) {
    val fill = if (enabled) color else lerp(color, Cartoon.Grey, 0.7f)
    Row(
        modifier
            .heightIn(min = height)
            .cartoon(fill, RoundedCornerShape(18.dp), shadow = if (enabled) 4.dp else 0.dp, onClick = onClick, enabled = enabled)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null && !trailingIcon) Icon(icon, null, tint = contentColor, modifier = Modifier.size(20.dp))
        Text(text, style = textStyle, color = contentColor, maxLines = 1)
        if (icon != null && trailingIcon) Icon(icon, null, tint = contentColor, modifier = Modifier.size(20.dp))
    }
}

/** 48dp square icon button used in headers. */
@Composable
fun InkIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Cartoon.Paper,
    contentColor: Color = Cartoon.Ink,
    size: Dp = 48.dp,
    badge: Int? = null
) {
    Box(modifier) {
        Box(
            Modifier
                .size(size)
                .cartoon(color, RoundedCornerShape(16.dp), shadow = 3.dp, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(22.dp))
        }
        if (badge != null && badge > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(20.dp)
                    .background(Cartoon.Red, CircleShape)
                    .border(InkWidth, Cartoon.Ink, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (badge > 9) "9+" else badge.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f),
                    color = Color.White
                )
            }
        }
    }
}

/** Filter / choice chip. Selected chips fill with [selectedColor] and pop up on a shadow. */
@Composable
fun CartoonChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = Cartoon.Blue,
    selectedContent: Color = Color.White,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier
            .height(44.dp)
            .cartoon(
                color = if (selected) selectedColor else Cartoon.Paper,
                shape = CircleShape,
                shadow = if (selected) 3.dp else 0.dp,
                onClick = onClick,
                role = Role.Tab
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        leading?.invoke()
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize),
            color = if (selected) selectedContent else Cartoon.Ink,
            maxLines = 1
        )
    }
}

/** Small outlined pill such as "Fresh" or "Heads up!". */
@Composable
fun InkTag(text: String, fill: Color, content: Color, modifier: Modifier = Modifier, uppercase: Boolean = false) {
    Text(
        if (uppercase) text.uppercase() else text,
        style = if (uppercase) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
        color = content,
        maxLines = 1,
        modifier = modifier
            .background(fill, CircleShape)
            .border(2.5.dp, Cartoon.Ink, CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
fun StatusTag(tone: StatusTone, modifier: Modifier = Modifier, text: String = tone.label) =
    InkTag(text, tone.fill, tone.content, modifier)

/** Outlined progress bar that grows in on first show. */
@Composable
fun InkProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 12.dp, minFraction: Float = 0.08f) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    val target by animateFloatAsState(fraction.coerceIn(minFraction, 1f), tween(500), label = "bar")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(Cartoon.Paper)
            .border(2.5.dp, Cartoon.Ink, CircleShape)
    ) {
        Box(
            Modifier
                .fillMaxWidth(target * grow.value)
                .height(height)
                .background(color)
                .drawBehind {
                    val w = 2.5.dp.toPx()
                    drawRect(Cartoon.Ink, Offset(size.width - w, 0f), Size(w, size.height))
                }
        )
    }
}

/** A row of outlined segments, e.g. fresh / soon / expired counts. */
@Composable
fun SegmentBar(segments: List<Pair<Int, Color>>, modifier: Modifier = Modifier, height: Dp = 18.dp) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(Cartoon.Paper)
            .border(InkWidth, Cartoon.Ink, CircleShape)
    ) {
        val visible = segments.filter { it.first > 0 }
        visible.forEachIndexed { index, (count, color) ->
            val weight by animateFloatAsState(count.toFloat(), tween(500), label = "segment")
            Box(
                Modifier
                    .weight(weight.coerceAtLeast(0.01f))
                    .height(height)
                    .graphicsLayer { alpha = grow.value }
                    .background(color)
                    .drawBehind {
                        if (index > 0) drawRect(Cartoon.Ink, size = Size(InkWidth.toPx(), size.height))
                    }
            )
        }
    }
}

/** Outlined toggle: blue track and a white knob that slides across. */
@Composable
fun CartoonSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val position by animateFloatAsState(
        if (checked) 1f else 0f,
        spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "knob"
    )
    Box(
        modifier
            .size(width = 60.dp, height = 34.dp)
            .clip(CircleShape)
            .background(lerp(Cartoon.Grey, Cartoon.Blue, position))
            .border(InkWidth, Cartoon.Ink, CircleShape)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(6.dp)
    ) {
        Box(
            Modifier
                .offset(x = 26.dp * position)
                .size(22.dp)
                .background(Cartoon.Paper, CircleShape)
                .border(2.5.dp, Cartoon.Ink, CircleShape)
        )
    }
}

/** Fredoka label shown above a form field. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Cartoon.Ink, modifier = modifier)
}

/** Grey uppercase section heading. */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.titleSmall.copy(letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing),
        color = Cartoon.InkMuted,
        modifier = modifier
    )
}

/** Outlined text input with a hard shadow. [fill] lets callers flash it after an auto-fill. */
@Composable
fun CartoonTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isError: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    fill: Color = Cartoon.Paper,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    readOnly: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        readOnly = readOnly,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Cartoon.Ink, fontWeight = FontWeight.Bold),
        cursorBrush = SolidColor(Cartoon.Blue),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = VisualTransformation.None,
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .cartoon(fill, shape, shadow = 3.dp, borderColor = if (isError) Cartoon.Red else Cartoon.Ink)
                    .padding(start = 14.dp, end = if (trailing != null) 4.dp else 14.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = Cartoon.Placeholder, maxLines = 1)
                    }
                    inner()
                }
                trailing?.invoke(this)
            }
        }
    )
}

/** Small red helper text shown below an invalid field. */
@Composable
fun FieldError(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Cartoon.Red, modifier = Modifier.padding(start = 4.dp))
}

/** Round header row: back button, title and optional trailing actions, below the status bar. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backIcon: ImageVector = InkIcons.Back,
    backLabel: String = "Back",
    titleColor: Color = Cartoon.Ink,
    centered: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val style = if (centered) {
        MaterialTheme.typography.titleLarge.copy(shadow = inkTextShadow(2.dp))
    } else MaterialTheme.typography.headlineLarge
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (onBack != null) InkIconButton(backIcon, backLabel, onBack)
        Text(
            title,
            style = style,
            color = titleColor,
            maxLines = 1,
            modifier = Modifier.weight(1f),
            textAlign = if (centered) TextAlign.Center else TextAlign.Start
        )
        actions()
    }
}

/** The ink drop shadow used on white headline text over blue. */
@Composable
fun inkTextShadow(offset: Dp): Shadow {
    val px = with(LocalDensity.current) { offset.toPx() }
    return Shadow(Cartoon.Ink, Offset(0f, px), 0f)
}

/** Number that counts up/down to [value]. */
@Composable
fun AnimatedCount(value: Int, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(value) {
        animated.animateTo(value.toFloat(), tween(durationMillis = 750, easing = FastOutSlowInEasing))
    }
    Text(animated.value.toInt().toString(), style = style, modifier = modifier, color = color)
}

/** Fade + rise entrance, played once per item. */
@Composable
fun Modifier.entrance(animate: Boolean, delayMillis: Int = 0): Modifier {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) {
            delay(delayMillis.toLong())
            progress.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 260f))
        }
    }
    return graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 22.dp.toPx()
        val s = 0.92f + 0.08f * progress.value
        scaleX = s
        scaleY = s
    }
}

/** Fill colour that flashes [glow] each time [tick] changes. */
@Composable
fun autoFillFlash(tick: Int, rest: Color = Cartoon.Paper, glow: Color = Cartoon.YellowSoft): Color {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(tick) {
        if (tick > 0) {
            progress.snapTo(1f)
            progress.animateTo(0f, tween(1400, easing = FastOutSlowInEasing))
        }
    }
    return lerp(rest, glow, progress.value)
}

/** Gives [content] a modifier that shakes horizontally whenever [trigger] changes while [enabled]. */
@Composable
fun Shake(trigger: Int, enabled: Boolean, content: @Composable (Modifier) -> Unit) {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger > 0 && enabled) {
            for (target in listOf(14f, -12f, 9f, -6f, 3f, 0f)) {
                offset.animateTo(target, spring(stiffness = 4000f))
            }
        }
    }
    content(Modifier.graphicsLayer { translationX = offset.value * density })
}

/** Forces light (white) status bar icons while on a blue screen. */
@Composable
fun LightStatusBarIcons() {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    DisposableEffect(activity) {
        val insets = WindowCompat.getInsetsController(activity.window, view)
        val previous = insets.isAppearanceLightStatusBars
        insets.isAppearanceLightStatusBars = false
        onDispose { insets.isAppearanceLightStatusBars = previous }
    }
}

/** Faint white ring/blob used as decoration on blue backgrounds. */
@Composable
fun DecorCircle(modifier: Modifier, ring: Dp = 0.dp, alpha: Float = 0.1f) {
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .then(
                if (ring > 0.dp) Modifier.border(ring, Color.White, CircleShape)
                else Modifier.background(Color.White, CircleShape)
            )
    )
}

/** Outlined pop-up card with a title, body and up to two actions. */
@Composable
fun CartoonDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = "Cancel",
    confirmColor: Color = Cartoon.Blue,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .cartoon(Cartoon.Paper, RoundedCornerShape(28.dp), shadow = 6.dp)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Cartoon.Ink)
            content()
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (dismissText != null) {
                    CartoonButton(
                        dismissText, onDismiss, Modifier.weight(1f),
                        color = Cartoon.Paper, contentColor = Cartoon.Ink, height = 50.dp
                    )
                }
                CartoonButton(
                    confirmText, onConfirm, Modifier.weight(1f),
                    color = confirmColor, enabled = confirmEnabled, height = 50.dp
                )
            }
        }
    }
}
