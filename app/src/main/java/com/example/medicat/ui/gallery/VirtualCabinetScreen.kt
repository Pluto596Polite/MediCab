package com.example.medicat.ui.gallery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medicat.ui.inventory.InventoryViewModel
import com.example.medicat.ui.inventory.MedicationItem
import com.example.medicat.ui.pack.Pack3D
import com.example.medicat.ui.pack.packSpec
import androidx.compose.foundation.layout.navigationBarsPadding
import com.example.medicat.ui.components.CartoonButton
import com.example.medicat.ui.components.InkIconButton
import com.example.medicat.ui.components.InkIcons
import com.example.medicat.ui.components.InkWidth
import com.example.medicat.ui.components.ScreenHeader
import com.example.medicat.ui.components.cartoon
import com.example.medicat.ui.theme.Cartoon

/** One plank of the cabinet. [label] is set on the first shelf of each category. */
private data class Shelf(val key: String, val label: String?, val count: Int, val items: List<MedicationItem>)

private fun buildShelves(items: List<MedicationItem>, columns: Int): List<Shelf> =
    items.groupBy { it.medication.category.trim().ifBlank { "Other" } }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
        .flatMap { (category, group) ->
            group.chunked(columns).mapIndexed { i, chunk ->
                Shelf("$category#$i", if (i == 0) category else null, group.size, chunk)
            }
        }

/** Wood and wall tones for the cabinet interior. */
private data class CabinetColors(
    val wall: Color,
    val wallShadow: Color,
    val plankTop: Color,
    val plankFront: Color,
    val plankUnder: Color,
    val door: Color,
    val doorPanel: Color,
    val handle: Color,
    val tag: Color,
    val tagText: Color
)

/** Cabinet in the cartoon style of the splash: pale blue inside, ink shelves, white doors. */
private val Colors = CabinetColors(
    wall = Cartoon.BlueSoft, wallShadow = Cartoon.BlueLight,
    plankTop = Cartoon.Paper, plankFront = Cartoon.Ink, plankUnder = Color(0xFF2A2D33),
    door = Cartoon.Paper, doorPanel = Cartoon.Ground, handle = Cartoon.Ink,
    tag = Cartoon.Paper, tagText = Cartoon.Ink
)

@Composable
fun VirtualCabinetScreen(
    onBack: () -> Unit,
    onOpenBox: (Int) -> Unit,
    onAddMedication: () -> Unit,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = Colors
    var doorsOpened by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Cartoon.Ground)) {
        ScreenHeader("Shelves", onBack = onBack) {
            InkIconButton(InkIcons.Plus, "Add medication", onAddMedication, color = Cartoon.Blue, contentColor = Color.White)
        }
        if (state.items.isNotEmpty()) {
            Text(
                "${state.items.size} items · ${state.expiringCount + state.expiredCount} need a look",
                style = MaterialTheme.typography.bodyMedium,
                color = Cartoon.InkMuted,
                modifier = Modifier.padding(start = 20.dp, top = 6.dp)
            )
        }
        BoxWithConstraints(
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
                .navigationBarsPadding()
                .fillMaxSize()
                .cartoon(colors.wall, RoundedCornerShape(24.dp), shadow = 5.dp)
                .cabinetInterior(colors)
        ) {
            val columns = (maxWidth / 112.dp).toInt().coerceIn(3, 7)
            val sidePadding = 18.dp
            val cellWidth = (maxWidth - sidePadding * 2) / columns

            when {
                state.isLoading -> Unit
                state.items.isEmpty() -> EmptyCabinet(onAddMedication, Modifier.align(Alignment.Center))
                else -> {
                    val shelves = remember(state.items, columns) { buildShelves(state.items, columns) }
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(shelves, key = { _, shelf -> shelf.key }) { index, shelf ->
                            ShelfRow(
                                shelf = shelf,
                                index = index,
                                listState = listState,
                                columns = columns,
                                cellWidth = cellWidth,
                                sidePadding = sidePadding,
                                colors = colors,
                                onOpenBox = onOpenBox
                            )
                        }
                    }
                }
            }
            SideWalls(colors)
            if (!doorsOpened && state.items.isNotEmpty()) {
                CabinetDoors(colors, onOpened = { doorsOpened = true })
            }
        }
    }
}

@Composable
private fun ShelfRow(
    shelf: Shelf,
    index: Int,
    listState: LazyListState,
    columns: Int,
    cellWidth: Dp,
    sidePadding: Dp,
    colors: CabinetColors,
    onOpenBox: (Int) -> Unit
) {
    // -1 when the shelf is at the top of the screen, +1 at the bottom. Shelves
    // below eye level show their tops; shelves above show their undersides.
    val tilt by remember(index) {
        derivedStateOf {
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return@derivedStateOf 0f
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            val centre = info.viewportStartOffset + viewport / 2f
            ((item.offset + item.size / 2f - centre) / (viewport / 2f)).coerceIn(-1.2f, 1.2f)
        }
    }
    val pitch = tilt * 18f

    // Every pack's base sits on one line, [standHeight] below the top of the row,
    // in the middle of the plank's top surface, so mixed templates line up neatly.
    val standHeight = cellWidth * 1.3f
    val floor = cellWidth * 0.3f
    val plankTop = PlankTopDepth * tilt.coerceAtLeast(0f)

    Box(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Plank(
            label = shelf.label,
            count = shelf.count,
            tilt = tilt,
            colors = colors,
            modifier = Modifier.padding(top = standHeight - plankTop / 2)
        )
        Row(Modifier.padding(horizontal = sidePadding)) {
            for (column in 0 until columns) {
                val item = shelf.items.getOrNull(column)
                Box(Modifier.width(cellWidth), contentAlignment = Alignment.TopCenter) {
                    if (item != null) {
                        // Turn each pack a little towards the middle of the shelf.
                        val relative = ((column + 0.5f) / columns - 0.5f) * 2f
                        CabinetPack(
                            item = item,
                            yaw = -relative * 16f,
                            pitch = pitch,
                            width = cellWidth * 0.9f,
                            height = standHeight,
                            floor = floor,
                            onClick = { onOpenBox(item.medication.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CabinetPack(
    item: MedicationItem,
    yaw: Float,
    pitch: Float,
    width: Dp,
    height: Dp,
    floor: Dp,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lift by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "lift"
    )
    Pack3D(
        spec = item.packSpec(),
        yaw = yaw * (1f - lift * 0.6f),
        pitch = pitch,
        maxWidth = width,
        maxHeight = height,
        floor = floor,
        modifier = Modifier
            .graphicsLayer {
                translationY = -lift * 10.dp.toPx()
                scaleX = 1f + lift * 0.06f
                scaleY = 1f + lift * 0.06f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, height / (height + floor))
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    )
}

private val PlankTopDepth = 16.dp
private val PlankFront = 22.dp
private val PlankUnderDepth = 12.dp

/** Shelf board drawn in perspective: top surface or underside depending on [tilt], plus a labelled front edge. */
@Composable
private fun Plank(label: String?, count: Int, tilt: Float, colors: CabinetColors, modifier: Modifier = Modifier) {
    val top = PlankTopDepth * tilt.coerceAtLeast(0f)
    val under = PlankUnderDepth * (-tilt).coerceAtLeast(0f)
    Box(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(top + PlankFront + under + 18.dp)) {
            val inset = 18.dp.toPx()
            val topPx = top.toPx()
            val frontPx = PlankFront.toPx()
            val underPx = under.toPx()
            val recede = 14.dp.toPx()
            if (topPx > 0.5f) {
                drawPath(
                    Path().apply {
                        moveTo(inset + recede * tilt, 0f)
                        lineTo(size.width - inset - recede * tilt, 0f)
                        lineTo(size.width - inset, topPx)
                        lineTo(inset, topPx)
                        close()
                    },
                    Brush.verticalGradient(listOf(colors.plankTop.copy(alpha = 0.75f), colors.plankTop), 0f, topPx)
                )
            }
            drawRect(
                Brush.verticalGradient(listOf(colors.plankFront, colors.plankUnder), topPx, topPx + frontPx),
                topLeft = Offset(inset, topPx),
                size = Size(size.width - inset * 2, frontPx)
            )
            // Thin highlight along the front edge.
            drawRect(Color.White.copy(alpha = 0.18f), Offset(inset, topPx), Size(size.width - inset * 2, 1.dp.toPx()))
            val bottom = topPx + frontPx
            if (underPx > 0.5f) {
                drawPath(
                    Path().apply {
                        moveTo(inset, bottom)
                        lineTo(size.width - inset, bottom)
                        lineTo(size.width - inset - recede * -tilt, bottom + underPx)
                        lineTo(inset + recede * -tilt, bottom + underPx)
                        close()
                    },
                    colors.plankUnder
                )
            }
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.16f), Color.Transparent),
                    bottom + underPx,
                    bottom + underPx + 18.dp.toPx()
                ),
                topLeft = Offset(inset, bottom + underPx),
                size = Size(size.width - inset * 2, 18.dp.toPx())
            )
        }
        if (label != null) {
            Text(
                "${label.uppercase()}  ·  $count",
                style = MaterialTheme.typography.labelSmall,
                color = colors.tagText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 30.dp, top = top + 3.dp)
                    .background(colors.tag, RoundedCornerShape(6.dp))
                    .border(2.dp, Cartoon.Ink, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

/** Back panel with a soft top light and darker corners, so the shelves sit inside a box. */
private fun Modifier.cabinetInterior(colors: CabinetColors): Modifier = drawBehind {
    drawRect(colors.wall)
    drawRect(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
            center = Offset(size.width / 2, 0f),
            radius = size.width
        )
    )
    // Faint vertical grain on the back panel.
    val step = 9.dp.toPx()
    var x = 0f
    var i = 0
    while (x < size.width) {
        drawLine(colors.wallShadow.copy(alpha = if (i % 3 == 0) 0.12f else 0.06f), Offset(x, 0f), Offset(x, size.height), 1f)
        x += step * (1f + (i * 37 % 5) / 10f)
        i++
    }
}

/** Inner side walls fading into the back panel. */
@Composable
private fun SideWalls(colors: CabinetColors) {
    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(18.dp)
                .background(Brush.horizontalGradient(listOf(colors.wallShadow, Color.Transparent)))
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxHeight()
                .width(18.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, colors.wallShadow)))
        )
    }
}

/** Two doors that swing open when the cabinet is first shown. */
@Composable
private fun CabinetDoors(colors: CabinetColors, onOpened: () -> Unit) {
    val swing = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        swing.animateTo(1f, tween(durationMillis = 1000, delayMillis = 450, easing = FastOutSlowInEasing))
        onOpened()
    }
    val angle = swing.value * 88f
    val fade = ((1f - swing.value) / 0.35f).coerceIn(0f, 1f)
    Row(Modifier.fillMaxSize()) {
        for (left in listOf(true, false)) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(if (left) 0f else 1f, 0.5f)
                        rotationY = if (left) -angle else angle
                        cameraDistance = 24f * density
                        alpha = fade
                    }
                    .background(colors.door)
                    .border(InkWidth, Cartoon.Ink)
                    .padding(10.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colors.doorPanel, RoundedCornerShape(12.dp))
                        .border(InkWidth, colors.handle, RoundedCornerShape(12.dp))
                )
                // Blue cross, as on the splash cabinet.
                Box(Modifier.align(Alignment.Center).size(width = 18.dp, height = 56.dp).background(Cartoon.Blue, RoundedCornerShape(5.dp)))
                Box(Modifier.align(Alignment.Center).size(width = 56.dp, height = 18.dp).background(Cartoon.Blue, RoundedCornerShape(5.dp)))
                Box(
                    Modifier
                        .align(if (left) Alignment.CenterEnd else Alignment.CenterStart)
                        .offset(x = if (left) (-6).dp else 6.dp)
                        .size(width = 12.dp, height = 40.dp)
                        .background(colors.handle, RoundedCornerShape(6.dp))
                )
            }
        }
    }
}

@Composable
private fun EmptyCabinet(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Empty shelves", style = MaterialTheme.typography.headlineMedium, color = Cartoon.Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "Scan your first pack and it will appear here on a shelf.",
            style = MaterialTheme.typography.bodyMedium,
            color = Cartoon.InkMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        CartoonButton("Add medication", onAdd, color = Cartoon.Red, icon = InkIcons.Plus)
    }
}
