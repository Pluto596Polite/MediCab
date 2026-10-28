package com.example.medicat.ui.gallery

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medicat.domain.ExpiryDates
import com.example.medicat.ui.components.CartoonButton
import com.example.medicat.ui.components.DecorCircle
import com.example.medicat.ui.components.InkIconButton
import com.example.medicat.ui.components.InkIcons
import com.example.medicat.ui.components.InkWidth
import com.example.medicat.ui.components.LightStatusBarIcons
import com.example.medicat.ui.components.MedArt
import com.example.medicat.ui.components.ScreenHeader
import com.example.medicat.ui.components.SectionHeading
import com.example.medicat.ui.components.cartoon
import com.example.medicat.ui.components.inkTextShadow
import com.example.medicat.ui.components.medArtFor
import com.example.medicat.ui.inventory.InventoryViewModel
import com.example.medicat.ui.inventory.MedicationItem
import com.example.medicat.ui.pack.Pack3D
import com.example.medicat.ui.pack.packSpec
import com.example.medicat.ui.theme.Cartoon
import kotlinx.coroutines.launch

private const val RestPitch = 14f
private const val RestYaw = -32f

/** "3D view": one medicine box on a pedestal that you can spin, with the rest of the cabinet below. */
@Composable
fun CabinetGalleryScreen(
    focusId: Int,
    onBack: () -> Unit,
    onOpenMedication: (Int) -> Unit,
    onAddMedication: () -> Unit,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val items = state.active
    LightStatusBarIcons()

    var selectedId by rememberSaveable { mutableIntStateOf(focusId) }
    val current = items.firstOrNull { it.medication.id == selectedId } ?: items.firstOrNull()

    var yaw by remember { mutableFloatStateOf(RestYaw) }
    var pitch by remember { mutableFloatStateOf(RestPitch) }
    var spinning by rememberSaveable { mutableStateOf(true) }
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Gentle idle rotation while not dragging.
    LaunchedEffect(spinning, dragging) {
        if (!spinning || dragging) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                yaw += (now - last) / 1_000_000_000f * 28f
                last = now
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Cartoon.Blue)) {
        DecorCircle(Modifier.align(Alignment.TopEnd).offset(190.dp, 110.dp).size(380.dp), ring = 50.dp, alpha = 0.08f)
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("3D view", onBack = onBack, titleColor = Color.White, centered = true) {
                InkIconButton(InkIcons.Reset, "Reset view", {
                    spinning = false
                    scope.launch { animate(yaw, RestYaw + 360f * Math.round((yaw - RestYaw) / 360f), animationSpec = spring(0.6f)) { v, _ -> yaw = v } }
                    scope.launch { animate(pitch, RestPitch, animationSpec = spring(0.6f)) { v, _ -> pitch = v } }
                })
            }
            if (current == null) {
                EmptyShelf(onAddMedication, Modifier.weight(1f))
                return@Column
            }

            AnimatedContent(
                targetState = current,
                contentKey = { it.medication.id },
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 3 }) togetherWith (fadeOut() + slideOutVertically { -it / 3 })
                },
                label = "title"
            ) { shown ->
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
                    Text(
                        shown.medication.name,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = MaterialTheme.typography.displaySmall.fontSize * 0.9f,
                            shadow = inkTextShadow(3.dp)
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOf(
                            if (shown.daysUntilExpiry < 0) "Expired ${ExpiryDates.relative(shown.daysUntilExpiry)}"
                            else "Expires ${ExpiryDates.format(shown.medication.expiryDate)}",
                            shown.medication.location
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }

            // Stage: drag anywhere here to spin.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .semantics { contentDescription = "3D model of ${current.medication.name}. Drag to rotate." }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false }
                        ) { change, drag ->
                            change.consume()
                            yaw += drag.x * 0.45f
                            pitch = (pitch - drag.y * 0.35f).coerceIn(-60f, 60f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Pedestal(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp))
                val floor = 90.dp
                Pack3D(
                    spec = current.packSpec(),
                    yaw = yaw,
                    pitch = pitch,
                    maxWidth = 230.dp,
                    maxHeight = 220.dp,
                    floor = floor,
                    // Base centre on the middle of the pedestal top (54dp up from the bottom).
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = floor - 54.dp)
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
            ) {
                PillToggle("Auto-spin", InkIcons.Spin, spinning) { spinning = !spinning }
                PillToggle("Details", InkIcons.Forward, false) { onOpenMedication(current.medication.id) }
            }
            Text(
                "Drag to spin!",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp)
            )

            ShelfSheet(items, current.medication.id) { selectedId = it }
        }
    }
}

@Composable
private fun Pedestal(modifier: Modifier = Modifier) {
    Box(modifier.size(width = 240.dp, height = 64.dp)) {
        Box(
            Modifier
                .offset(y = 8.dp)
                .size(width = 240.dp, height = 56.dp)
                .background(Cartoon.BlueDeep, CircleShape)
                .border(InkWidth, Cartoon.Ink, CircleShape)
        )
        Box(
            Modifier
                .size(width = 240.dp, height = 56.dp)
                .background(Cartoon.Paper, CircleShape)
                .border(InkWidth, Cartoon.Ink, CircleShape)
        )
    }
}

@Composable
private fun PillToggle(label: String, icon: ImageVector, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(46.dp)
            .cartoon(if (on) Cartoon.Red else Cartoon.Paper, CircleShape, shadow = 3.dp, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val tint = if (on) Color.White else Cartoon.Ink
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize), color = tint)
    }
}

/** White sheet listing every medication; tap one to put it on the pedestal. */
@Composable
private fun ShelfSheet(items: List<MedicationItem>, selectedId: Int, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedId) {
        val index = items.indexOfFirst { it.medication.id == selectedId }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Cartoon.Ink, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .padding(top = InkWidth)
            .background(Cartoon.Paper, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .navigationBarsPadding()
            .padding(top = 16.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeading("Your meds", Modifier.padding(horizontal = 20.dp))
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(items, key = { _, it -> it.medication.id }) { _, item ->
                val selected = item.medication.id == selectedId
                Column(
                    Modifier
                        .width(104.dp)
                        .height(104.dp)
                        .cartoon(
                            if (selected) Cartoon.BlueSoft else Cartoon.Paper,
                            RoundedCornerShape(20.dp),
                            shadow = if (selected) 4.dp else 0.dp,
                            onClick = { onSelect(item.medication.id) }
                        )
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    MedArt(item.packSpec().template.art)
                    Text(
                        item.medication.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = Cartoon.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyShelf(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Nothing to spin yet",
            style = MaterialTheme.typography.headlineMedium.copy(shadow = inkTextShadow(3.dp)),
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Add medication and it shows up here as a box you can turn around.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        CartoonButton("Add medication", onAdd, color = Cartoon.Red, icon = InkIcons.Plus)
    }
}
