package com.example.medicat.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medicat.data.local.MedicationEntity
import com.example.medicat.domain.ExpiryDates
import com.example.medicat.domain.ExpiryStatus
import com.example.medicat.ui.components.AnimatedCount
import com.example.medicat.ui.components.CartoonButton
import com.example.medicat.ui.components.CartoonCard
import com.example.medicat.ui.components.CartoonDialog
import com.example.medicat.ui.components.CartoonSwitch
import com.example.medicat.ui.components.DecorCircle
import com.example.medicat.ui.components.InkIconButton
import com.example.medicat.ui.components.InkIcons
import com.example.medicat.ui.components.InkProgressBar
import com.example.medicat.ui.components.InkTag
import com.example.medicat.ui.components.InkWidth
import com.example.medicat.ui.components.LightStatusBarIcons
import com.example.medicat.ui.components.MedArt
import com.example.medicat.ui.components.cartoon
import com.example.medicat.ui.components.inkTextShadow
import com.example.medicat.ui.components.medArtFor
import com.example.medicat.ui.components.stockUnit
import com.example.medicat.ui.pack.Pack3D
import com.example.medicat.ui.pack.packSpec
import com.example.medicat.ui.theme.Cartoon
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween

@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onEdit: (Int) -> Unit,
    onView3D: (Int) -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gone by viewModel.gone.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(gone, state.isLoading, state.medication) {
        if (gone || (!state.isLoading && state.medication == null)) onBack()
    }
    LightStatusBarIcons()
    val loggedDose by viewModel.loggedDose.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(loggedDose) {
        if (loggedDose == null) return@LaunchedEffect
        val result = snackbar.showSnackbar("Dose logged", actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDose() else viewModel.doseNoticeShown()
    }

    val med = state.medication ?: run {
        Box(Modifier.fillMaxSize().background(Cartoon.Ground))
        return
    }

    Box(Modifier.fillMaxSize().background(Cartoon.Ground)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 120.dp)
        ) {
            Hero(
                med = med,
                status = state.status,
                days = state.daysUntilExpiry,
                onBack = onBack,
                onView3D = { onView3D(med.id) },
                onEdit = { onEdit(med.id) },
                onToggleActive = { viewModel.setActive(!med.isActive) },
                onDelete = { confirmDelete = true }
            )
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Fact("Expires", ExpiryDates.format(med.expiryDate), Modifier.weight(1f))
                    Fact("Kept in", med.location.ifBlank { "Not set" }, Modifier.weight(1f))
                }
                StockCard(med, onLess = viewModel::oneLess, onMore = viewModel::addOne)
                ReminderCard(
                    remind = med.remind,
                    reminderDays = state.reminderDays,
                    onChange = viewModel::setRemind
                )
                if (med.description.isNotBlank() || !med.barcode.isNullOrBlank()) {
                    CartoonCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Text("Notes", style = MaterialTheme.typography.bodySmall, color = Cartoon.InkMuted)
                        if (med.description.isNotBlank()) {
                            Text(med.description, style = MaterialTheme.typography.bodyMedium, color = Cartoon.Ink)
                        }
                        med.barcode?.takeIf { it.isNotBlank() }?.let {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                Icon(InkIcons.Barcode, null, tint = Cartoon.InkMuted, modifier = Modifier.size(16.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall, color = Cartoon.InkMuted)
                            }
                        }
                    }
                }
            }
        }

        // Actions pinned to the bottom.
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CartoonButton("Edit", { onEdit(med.id) }, color = Cartoon.Paper, contentColor = Cartoon.Ink)
            when {
                !med.isActive -> CartoonButton(
                    "Back in cabinet", { viewModel.setActive(true) }, Modifier.weight(1f), icon = InkIcons.Undo
                )
                med.quantity > 0 && med.remaining == 0 -> CartoonButton(
                    "Mark used up", { viewModel.setActive(false) }, Modifier.weight(1f), color = Cartoon.Red
                )
                else -> CartoonButton(
                    "Log a dose", viewModel::logDose, Modifier.weight(1f)
                )
            }
        }
        SnackbarHost(
            snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp)
        ) { data ->
            Snackbar(
                data,
                shape = RoundedCornerShape(16.dp),
                containerColor = Cartoon.Ink,
                contentColor = Color.White,
                actionColor = Cartoon.Yellow,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }

    if (confirmDelete) {
        CartoonDialog(
            title = "Delete ${med.name}?",
            onDismiss = { confirmDelete = false },
            confirmText = "Delete",
            confirmColor = Cartoon.Red,
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            }
        ) {
            Text(
                "This removes it for good. \"Mark used up\" keeps it around so you can add it again quickly.",
                style = MaterialTheme.typography.bodyMedium,
                color = Cartoon.InkMuted
            )
        }
    }
}

@Composable
private fun Hero(
    med: MedicationEntity,
    status: ExpiryStatus,
    days: Long,
    onBack: () -> Unit,
    onView3D: () -> Unit,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {
    val fill = if (status == ExpiryStatus.EXPIRED && med.isActive) Cartoon.Red else Cartoon.Blue
    val shape = RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)
    // Ink shape peeking out below the fill gives the curved bottom outline.
    Box(
        Modifier
            .fillMaxWidth()
            .background(Cartoon.Ink, shape)
            .padding(bottom = InkWidth)
            .clip(shape)
            .background(fill)
    ) {
        DecorCircle(Modifier.align(Alignment.TopEnd).offset(60.dp, 110.dp).size(240.dp), alpha = 0.12f)
        // The medication's 3D pack, gently turning.
        val turn = rememberInfiniteTransition(label = "turn")
        val yaw by turn.animateFloat(-35f, 35f, infiniteRepeatable(tween(3400), RepeatMode.Reverse), label = "yaw")
        Pack3D(
            spec = med.packSpec(),
            yaw = yaw - 15f,
            pitch = 12f,
            maxWidth = 130.dp,
            maxHeight = 150.dp,
            floor = 26.dp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 8.dp)
        )
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InkIconButton(InkIcons.Back, "Back", onBack)
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .height(48.dp)
                        .cartoon(if (fill == Cartoon.Red) Cartoon.Blue else Cartoon.Red, RoundedCornerShape(16.dp), shadow = 3.dp, onClick = onView3D)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(InkIcons.Cube, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text("View in 3D", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
                Spacer(Modifier.size(8.dp))
                var menu by rememberSaveable { mutableStateOf(false) }
                Box {
                    InkIconButton(InkIcons.More, "More options", { menu = true })
                    DropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false },
                        containerColor = Cartoon.Paper,
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(InkWidth, Cartoon.Ink)
                    ) {
                        MenuItem("Edit details", InkIcons.Pencil) { menu = false; onEdit() }
                        MenuItem(if (med.isActive) "Mark used up" else "Back in cabinet", InkIcons.Undo) {
                            menu = false; onToggleActive()
                        }
                        MenuItem("Delete", InkIcons.Trash, Cartoon.Red) { menu = false; onDelete() }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            val tag = when {
                !med.isActive -> "Used up"
                status == ExpiryStatus.SAFE -> "Fresh"
                status == ExpiryStatus.EXPIRING_SOON -> "Expiring soon"
                else -> "Expired"
            }
            InkTag(tag, Cartoon.Paper, Cartoon.Ink, uppercase = true)
            Spacer(Modifier.height(10.dp))
            Text(
                med.name,
                style = MaterialTheme.typography.displaySmall.copy(shadow = inkTextShadow(3.dp)),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 120.dp)
            )
            val sub = listOf(med.strength, med.form, med.category).filter { it.isNotBlank() }
            if (sub.isNotEmpty()) {
                Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodyLarge, color = Color.White)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AnimatedCount(
                    if (days < 0) (-days).toInt() else days.toInt(),
                    MaterialTheme.typography.displayLarge.copy(
                        fontSize = MaterialTheme.typography.displayLarge.fontSize * 1.15f,
                        shadow = inkTextShadow(5.dp)
                    ),
                    color = Color.White
                )
                Text(
                    if (days < 0) "days\nago" else if (days == 1L) "day\nleft" else "days\nleft",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, icon: ImageVector, color: Color = Cartoon.Ink, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.labelLarge, color = color) },
        leadingIcon = { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) },
        onClick = onClick
    )
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    CartoonCard(modifier, shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Cartoon.InkMuted)
        Text(value, style = MaterialTheme.typography.titleMedium, color = Cartoon.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StockCard(med: MedicationEntity, onLess: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .cartoon(Cartoon.Paper, RoundedCornerShape(20.dp))
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("In stock", style = MaterialTheme.typography.bodySmall, color = Cartoon.InkMuted)
            if (med.quantity > 0) {
                Text(
                    "${med.remaining} of ${med.quantity} ${stockUnit(med.form, med.quantity)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Cartoon.Ink
                )
                InkProgressBar(med.remaining / med.quantity.toFloat(), Cartoon.Blue, minFraction = 0.04f)
            } else {
                Text("Not counted", style = MaterialTheme.typography.titleMedium, color = Cartoon.Ink)
                Text("Set a pack size in Edit to count doses", style = MaterialTheme.typography.bodySmall, color = Cartoon.InkMuted)
            }
        }
        if (med.quantity > 0) {
            StepButton(InkIcons.Minus, "One less", Cartoon.Paper, Cartoon.Ink, onLess, enabled = med.remaining > 0)
            StepButton(InkIcons.Plus, "One more", Cartoon.Blue, Color.White, onMore, enabled = med.remaining < med.quantity)
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, fill: Color, tint: Color, onClick: () -> Unit, enabled: Boolean) {
    Box(
        Modifier
            .size(46.dp)
            .cartoon(if (enabled) fill else Cartoon.Grey, RoundedCornerShape(14.dp), shadow = if (enabled) 3.dp else 0.dp, onClick = onClick, enabled = enabled, onClickLabel = label)
            .graphicsLayer { alpha = if (enabled) 1f else 0.6f },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ReminderCard(remind: Boolean, reminderDays: Int, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .cartoon(Cartoon.Paper, RoundedCornerShape(20.dp), onClick = { onChange(!remind) }, role = androidx.compose.ui.semantics.Role.Switch)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(42.dp)
                .background(Cartoon.PinkSoft, RoundedCornerShape(14.dp))
                .border(2.5.dp, Cartoon.Ink, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(InkIcons.Bell, null, tint = Cartoon.Ink, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Expiry reminder", style = MaterialTheme.typography.titleMedium, color = Cartoon.Ink)
            Text(
                if (remind) "$reminderDays days before" else "Off for this one",
                style = MaterialTheme.typography.bodySmall,
                color = Cartoon.InkMuted
            )
        }
        CartoonSwitch(remind, onChange)
    }
}
