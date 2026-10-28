package com.example.medicat.ui.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.medicat.ui.components.CartoonButton
import com.example.medicat.ui.components.DecorCircle
import com.example.medicat.ui.components.InkIconButton
import com.example.medicat.ui.components.InkIcons
import com.example.medicat.ui.components.InkWidth
import com.example.medicat.ui.components.LightStatusBarIcons
import com.example.medicat.ui.components.MedArt
import com.example.medicat.ui.components.MedArtKind
import com.example.medicat.ui.components.ScreenHeader
import com.example.medicat.ui.components.cartoon
import com.example.medicat.ui.components.inkTextShadow
import com.example.medicat.ui.theme.Cartoon
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch

@Composable
fun ScannerScreen(
    onScanned: (String) -> Unit,
    onLabelDone: () -> Unit,
    onBack: () -> Unit,
    onTypeIn: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }
    // Picks up a permission granted from system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = context.hasCameraPermission()
    }
    LightStatusBarIcons()

    Box(Modifier.fillMaxSize().background(Cartoon.Blue)) {
        DecorCircle(Modifier.offset((-170).dp, (-120).dp).size(360.dp), ring = 50.dp, alpha = 0.08f)
        if (hasPermission) {
            CameraScanner(onScanned = onScanned, onLabelDone = onLabelDone, onBack = onBack, onTypeIn = onTypeIn)
        } else {
            PermissionRequest(
                onRequest = { launcher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                },
                onBack = onBack,
                onTypeIn = onTypeIn
            )
        }
    }
}

/** Barcode: read a code and go. Label: read the pack's text and photograph it for a 3D model. */
private enum class ScanMode(val label: String) { BARCODE("Barcode"), LABEL("Label") }

/** A line of recognised text, in preview coordinates, for the live highlight overlay. */
private class TextBox(val rect: android.graphics.Rect, val text: String)

@Composable
private fun CameraScanner(
    onScanned: (String) -> Unit,
    onLabelDone: () -> Unit,
    onBack: () -> Unit,
    onTypeIn: () -> Unit,
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val controller = remember {
        LifecycleCameraController(context).apply {
            // Label text is small: analyse at 720p rather than the default VGA.
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(android.util.Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build()
        }
    }
    val currentOnScanned by rememberUpdatedState(onScanned)
    var torchOn by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(ScanMode.LABEL) }
    var detected by remember { mutableStateOf<String?>(null) }
    var textBoxes by remember { mutableStateOf<List<TextBox>>(emptyList()) }
    val label by viewModel.state.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    DisposableEffect(mode) {
        val executor = ContextCompat.getMainExecutor(context)
        val barcodes = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_DATA_MATRIX,
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_CODE_128
                )
                .build()
        )
        val text = if (mode == ScanMode.LABEL) TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) else null
        val delivered = AtomicBoolean(false)

        controller.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(
                listOfNotNull(barcodes, text),
                // View-referenced boxes line up with the preview for the highlight overlay.
                ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                executor
            ) { result ->
                val raw = result.getValue(barcodes)
                    ?.firstNotNullOfOrNull { barcode -> barcode.rawValue?.takeIf { it.isNotBlank() } }
                if (text == null) {
                    if (raw != null && delivered.compareAndSet(false, true)) detected = raw
                    return@MlKitAnalyzer
                }
                raw?.let(viewModel::onBarcode)
                result.getValue(text)?.let { read ->
                    viewModel.onFrameText(read.toOcrLines())
                    textBoxes = read.textBlocks.flatMap { block ->
                        block.lines.mapNotNull { line -> line.boundingBox?.let { TextBox(it, line.text) } }
                    }
                }
            }
        )
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            textBoxes = emptyList()
            // Closing a detector should never take the app down with it.
            runCatching { barcodes.close() }
            runCatching { text?.close() }
        }
    }

    LaunchedEffect(torchOn) {
        controller.enableTorch(torchOn)
    }
    // Let the success state register before leaving the screen.
    LaunchedEffect(detected) {
        val raw = detected ?: return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(650)
        currentOnScanned(raw)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Scan medication",
            onBack = onBack,
            backIcon = InkIcons.Close,
            backLabel = "Close scanner",
            titleColor = Color.White,
            centered = true
        ) {
            InkIconButton(
                icon = InkIcons.Flash,
                contentDescription = if (torchOn) "Turn torch off" else "Turn torch on",
                onClick = { torchOn = !torchOn },
                color = if (torchOn) Cartoon.Yellow else Cartoon.Paper
            )
        }

        ModeSwitch(mode, onSelect = { mode = it; detected = null })

        // Viewfinder: the live camera inside an outlined, shadowed window.
        val shape = RoundedCornerShape(28.dp)
        Box(
            Modifier
                .padding(start = 28.dp, end = 28.dp, top = 14.dp)
                .fillMaxWidth()
                .weight(1f)
                .cartoon(Cartoon.BlueDeep, shape, shadow = 5.dp)
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        this.controller = controller
                    }
                },
                modifier = Modifier.fillMaxSize().clip(shape)
            )
            if (mode == ScanMode.LABEL) {
                TextHighlights(textBoxes, label.fields.name)
            } else {
                ScanOverlay(detected = detected != null)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                when {
                    mode == ScanMode.BARCODE -> if (detected != null) "Got it! Reading the pack…" else "Point at the barcode"
                    label.fields.name == null -> "Point at the front of the pack"
                    label.fields.expiry == null -> "Turn it to show the expiry date"
                    label.template == null -> "Got it! Now pick the pack type"
                    else -> "All read. Tap Use these"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Row(
                Modifier
                    .height(44.dp)
                    .cartoon(Cartoon.Paper, CircleShape, shadow = 3.dp, onClick = onTypeIn)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(InkIcons.Pencil, null, tint = Cartoon.Ink, modifier = Modifier.size(16.dp))
                Text("Type it in", style = MaterialTheme.typography.labelMedium, color = Cartoon.Ink)
            }
        }

        if (mode == ScanMode.LABEL) {
            LabelSheet(
                state = label,
                onChooseTemplate = viewModel::chooseTemplate,
                onReset = viewModel::reset,
                onDone = {
                    viewModel.finish()
                    onLabelDone()
                }
            )
        } else {
            TipsSheet(detected)
        }
    }
}

@Composable
private fun ModeSwitch(mode: ScanMode, onSelect: (ScanMode) -> Unit) {
    Row(
        Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .width(260.dp)
            .height(50.dp)
            .cartoon(Cartoon.Paper, CircleShape, shadow = 3.dp)
            .padding(3.dp)
    ) {
        ScanMode.entries.forEach { option ->
            val selected = option == mode
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (selected) Cartoon.Red else Color.Transparent)
                    .border(2.5.dp, if (selected) Cartoon.Ink else Color.Transparent, CircleShape)
                    .selectable(selected, role = Role.Tab) { onSelect(option) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize),
                    color = if (selected) Color.White else Cartoon.Ink
                )
            }
        }
    }
}

/** Lines of text the camera can read, with the product name lit up in yellow. */
@Composable
private fun TextHighlights(boxes: List<TextBox>, name: String?) {
    Canvas(Modifier.fillMaxSize()) {
        drawCornerBrackets(18.dp.toPx(), Color.White)
        val radius = CornerRadius(6.dp.toPx())
        boxes.forEach { box ->
            val isName = name != null && box.text.contains(name, ignoreCase = true)
            val topLeft = Offset(box.rect.left.toFloat() - 4, box.rect.top.toFloat() - 3)
            val size = Size(box.rect.width().toFloat() + 8, box.rect.height().toFloat() + 6)
            drawRoundRect(if (isName) Cartoon.Yellow.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.18f), topLeft, size, radius)
            if (isName) drawRoundRect(Cartoon.Ink, topLeft, size, radius, style = Stroke(2.5.dp.toPx()))
        }
    }
}

/** White sheet at the bottom: tips while scanning, a confirmation once a code is read. */
@Composable
private fun TipsSheet(detected: String?) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Cartoon.Ink, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .padding(top = InkWidth)
            .background(Cartoon.Paper, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 44.dp, height = 6.dp).background(Cartoon.Ink, CircleShape))
        AnimatedContent(
            targetState = detected,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
            label = "sheet"
        ) { code ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (code == null) "Scanning tips" else "Got it!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Cartoon.Ink
                )
                if (code == null) {
                    TipRow(MedArtKind.BOX, "Barcode", "Fills in the name from your cabinet or online")
                    TipRow(MedArtKind.BLISTER, "Square DataMatrix code", "Also reads the expiry date and batch")
                } else {
                    TipRow(MedArtKind.BOX, "Code", code.take(32), checked = true)
                }
            }
        }
    }
}

@Composable
private fun TipRow(art: MedArtKind, title: String, body: String, checked: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Cartoon.Ground, RoundedCornerShape(18.dp))
            .border(2.5.dp, Cartoon.Ink, RoundedCornerShape(18.dp))
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (checked) {
            Box(
                Modifier.padding(4.dp).size(30.dp).background(Cartoon.Blue, CircleShape).border(2.5.dp, Cartoon.Ink, CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(InkIcons.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
        } else {
            MedArt(art, size = 40.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = Cartoon.InkMuted)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Cartoon.Ink)
        }
    }
}

/** Chunky white corner brackets and a red laser line that sweeps the window. */
@Composable
private fun ScanOverlay(detected: Boolean) {
    val sweep = rememberInfiniteTransition(label = "laser")
    val laserY by sweep.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "laserY"
    )
    val frameColor by animateColorAsState(if (detected) Cartoon.Yellow else Color.White, label = "frame")

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 18.dp.toPx()
            drawCornerBrackets(inset, frameColor)
            if (!detected) {
                val y = size.height * laserY
                val left = 36.dp.toPx()
                val h = 8.dp.toPx()
                drawRoundRect(Cartoon.Red, Offset(left, y - h / 2), Size(size.width - left * 2, h), CornerRadius(h / 2))
                drawRoundRect(
                    Cartoon.Ink, Offset(left + 1.dp.toPx(), y - h / 2 + 1.dp.toPx()),
                    Size(size.width - left * 2 - 2.dp.toPx(), h - 2.dp.toPx()), CornerRadius(h / 2),
                    style = Stroke(2.dp.toPx())
                )
            }
        }
        AnimatedVisibility(
            visible = detected,
            enter = fadeIn() + scaleIn(initialScale = 0.3f, animationSpec = spring(dampingRatio = 0.45f)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                Modifier.size(84.dp).cartoon(Cartoon.Blue, CircleShape, shadow = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(InkIcons.Check, contentDescription = "Scanned", tint = Color.White, modifier = Modifier.size(44.dp))
            }
        }
    }
}

private fun DrawScope.drawCornerBrackets(inset: Float, color: Color) {
    val arm = 42.dp.toPx()
    val r = 18.dp.toPx()
    val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
    val w = stroke.width / 2
    val (l, t, rt, b) = listOf(inset + w, inset + w, size.width - inset - w, size.height - inset - w)
    val d = r * 2
    drawArc(color, 180f, 90f, false, Offset(l, t), Size(d, d), style = stroke)
    drawLine(color, Offset(l, t + r), Offset(l, t + arm), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(l + r, t), Offset(l + arm, t), stroke.width, StrokeCap.Round)

    drawArc(color, 270f, 90f, false, Offset(rt - d, t), Size(d, d), style = stroke)
    drawLine(color, Offset(rt, t + r), Offset(rt, t + arm), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(rt - r, t), Offset(rt - arm, t), stroke.width, StrokeCap.Round)

    drawArc(color, 0f, 90f, false, Offset(rt - d, b - d), Size(d, d), style = stroke)
    drawLine(color, Offset(rt, b - r), Offset(rt, b - arm), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(rt - r, b), Offset(rt - arm, b), stroke.width, StrokeCap.Round)

    drawArc(color, 90f, 90f, false, Offset(l, b - d), Size(d, d), style = stroke)
    drawLine(color, Offset(l, b - r), Offset(l, b - arm), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(l + r, b), Offset(l + arm, b), stroke.width, StrokeCap.Round)
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit, onOpenSettings: () -> Unit, onBack: () -> Unit, onTypeIn: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("", onBack = onBack, backIcon = InkIcons.Close, backLabel = "Close scanner")
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(120.dp).cartoon(Cartoon.Paper, CircleShape, shadow = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(InkIcons.Camera, null, tint = Cartoon.Ink, modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Camera, please!",
                style = MaterialTheme.typography.headlineLarge.copy(shadow = inkTextShadow(3.dp)),
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "MediCab only uses the camera to read barcodes. Nothing is recorded or leaves your phone.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Spacer(Modifier.height(28.dp))
            CartoonButton("Allow camera", onRequest, Modifier.fillMaxWidth(), color = Cartoon.Red, icon = InkIcons.Camera)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CartoonButton("Settings", onOpenSettings, Modifier.weight(1f), color = Cartoon.Paper, contentColor = Cartoon.Ink, height = 50.dp)
                CartoonButton("Type it in", onTypeIn, Modifier.weight(1f), color = Cartoon.Paper, contentColor = Cartoon.Ink, height = 50.dp)
            }
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
