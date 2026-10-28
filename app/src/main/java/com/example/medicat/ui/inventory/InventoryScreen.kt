package com.example.medicat.ui.inventory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medicat.domain.ExpiryStatus
import com.example.medicat.ui.components.AnimatedCount
import com.example.medicat.ui.components.CartoonButton
import com.example.medicat.ui.components.CartoonCard
import com.example.medicat.ui.components.CartoonChip
import com.example.medicat.ui.components.CartoonTextField
import com.example.medicat.ui.components.DecorCircle
import com.example.medicat.ui.components.InkIconButton
import com.example.medicat.ui.components.InkIcons
import com.example.medicat.ui.components.InkProgressBar
import com.example.medicat.ui.components.InkTag
import com.example.medicat.ui.components.MedArt
import com.example.medicat.ui.components.MedArtKind
import com.example.medicat.ui.components.MediCabLogo
import com.example.medicat.ui.components.SegmentBar
import com.example.medicat.ui.components.StatusTag
import com.example.medicat.ui.components.cartoon
import com.example.medicat.ui.components.categoryTint
import com.example.medicat.ui.components.doseLine
import com.example.medicat.ui.components.entrance
import com.example.medicat.ui.components.inkTextShadow
import com.example.medicat.ui.components.medArtFor
import com.example.medicat.ui.pack.packSpec
import com.example.medicat.ui.theme.Cartoon
import com.example.medicat.ui.theme.tone
import java.time.LocalTime

/** Destinations reachable from the floating bottom bar. */
enum class HomeTab { CABINET, SHELF, SCAN, ALERTS, SETTINGS }

@Composable
fun InventoryScreen(
    onAddMedication: () -> Unit,
    onOpenMedication: (Int) -> Unit,
    onNavigate: (HomeTab) -> Unit,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    // Items that already played their entrance, so scrolling back doesn't replay it.
    val seen = remember { mutableSetOf<Any>() }
    val attention = state.expiringCount + state.expiredCount

    Box(Modifier.fillMaxSize().background(Cartoon.Ground)) {
        when {
            state.isLoading -> Unit
            state.isEmpty -> Column(Modifier.fillMaxSize()) {
                Header(attention = 0, searching = false, onToggleSearch = null, onAlerts = { onNavigate(HomeTab.ALERTS) })
                EmptyCabinet(
                    onScan = { onNavigate(HomeTab.SCAN) },
                    onAdd = onAddMedication,
                    modifier = Modifier.weight(1f)
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 132.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Header(
                            attention = attention,
                            searching = searching,
                            onToggleSearch = {
                                if (searching) viewModel.setQuery("")
                                searching = !searching
                            },
                            onAlerts = { onNavigate(HomeTab.ALERTS) },
                            modifier = Modifier.padding(horizontal = 0.dp)
                        )
                        AnimatedVisibility(searching, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            CartoonTextField(
                                value = state.query,
                                onValueChange = viewModel::setQuery,
                                placeholder = "Search name, category or notes",
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }
                }
                item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                    HeroCard(state, Modifier.entrance(animate = seen.add("hero"), delayMillis = 60))
                }
                item(key = "chips", span = { GridItemSpan(maxLineSpan) }) {
                    FilterChips(state, viewModel, Modifier.entrance(animate = seen.add("chips"), delayMillis = 120))
                }
                if (state.items.isEmpty()) {
                    item(key = "none", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = if (state.query.isBlank()) "Nothing here yet." else "No matches for \"${state.query}\".",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Cartoon.InkMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                        )
                    }
                }
                itemsIndexed(state.items, key = { _, it -> it.medication.id }) { index, item ->
                    MedicationCard(
                        item = item,
                        onClick = { onOpenMedication(item.medication.id) },
                        modifier = Modifier
                            .animateItem()
                            .entrance(animate = seen.add(item.medication.id), delayMillis = 160 + index.coerceAtMost(8) * 50)
                    )
                }
            }
        }

        BottomBar(
            onNavigate = onNavigate,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        )
    }
}

@Composable
private fun Header(
    attention: Int,
    searching: Boolean,
    onToggleSearch: (() -> Unit)?,
    onAlerts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MediCabLogo(size = 52.dp)
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.bodyMedium, color = Cartoon.InkMuted)
            Text("My Cabinet", style = MaterialTheme.typography.headlineLarge, color = Cartoon.Ink, maxLines = 1)
        }
        if (onToggleSearch != null) {
            InkIconButton(
                icon = if (searching) InkIcons.Close else InkIcons.Search,
                contentDescription = if (searching) "Close search" else "Search",
                onClick = onToggleSearch
            )
        }
        InkIconButton(
            icon = InkIcons.Bell,
            contentDescription = if (attention > 0) "Alerts, $attention need attention" else "Alerts",
            onClick = onAlerts,
            badge = attention
        )
    }
}

@Composable
private fun HeroCard(state: InventoryUiState, modifier: Modifier = Modifier) {
    val attention = state.expiringCount + state.expiredCount
    val total = state.safeCount + attention
    Box(
        modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .cartoon(Cartoon.Blue, RoundedCornerShape(26.dp), shadow = 5.dp)
    ) {
        DecorCircle(Modifier.align(Alignment.TopEnd).offset(40.dp, (-40).dp).size(150.dp), alpha = 0.12f)
        val wobble = rememberInfiniteTransition(label = "wobble")
        val tilt by wobble.animateFloat(4f, 16f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "tilt")
        MedArt(
            MedArtKind.CAPSULE,
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp, end = 18.dp)
                .graphicsLayer { rotationZ = tilt; scaleX = 1.4f; scaleY = 1.4f }
        )
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (attention > 0) InkTag("Heads up!", Cartoon.Red, Color.White, uppercase = true)
            else InkTag("All good!", Cartoon.Paper, Cartoon.Ink, uppercase = true)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AnimatedCount(
                    if (attention > 0) attention else total,
                    MaterialTheme.typography.displayLarge.copy(shadow = inkTextShadow(4.dp)),
                    color = Color.White
                )
                Text(
                    when {
                        attention == 1 -> "med needs a look in the next ${state.reminderDays} days"
                        attention > 1 -> "meds need a look in the next ${state.reminderDays} days"
                        total == 1 -> "med in your cabinet, all fresh"
                        else -> "meds in your cabinet, all fresh"
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 6.dp).width(170.dp)
                )
            }
            SegmentBar(
                listOf(
                    state.safeCount to Cartoon.Paper,
                    state.expiringCount to Cartoon.Pink,
                    state.expiredCount to Cartoon.Red
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Legend("${state.safeCount} fresh", Cartoon.Paper)
                Legend("${state.expiringCount} soon", Cartoon.Pink)
                Legend("${state.expiredCount} expired", Cartoon.Red)
            }
        }
    }
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).background(color, CircleShape).border(2.dp, Cartoon.Ink, CircleShape))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Color.White)
    }
}

@Composable
private fun FilterChips(state: InventoryUiState, viewModel: InventoryViewModel, modifier: Modifier = Modifier) {
    val chips = buildList {
        InventoryFilter.entries.forEach { add(it.label to it) }
        state.categories.forEach { add(it to it) }
    }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // Room for the chips' drop shadow and the grid's side padding to bleed.
        contentPadding = PaddingValues(bottom = 4.dp)
    ) {
        items(chips, key = { it.first + it.second.hashCode() }) { (label, value) ->
            val selected = when (value) {
                is InventoryFilter -> state.category == null && state.filter == value
                else -> state.category == value
            }
            val badge = when (value) {
                InventoryFilter.EXPIRING_SOON -> state.expiringCount
                InventoryFilter.EXPIRED -> state.expiredCount
                else -> 0
            }
            CartoonChip(
                label = if (badge > 0) "$label · $badge" else label,
                selected = selected,
                onClick = {
                    when (value) {
                        is InventoryFilter -> viewModel.setFilter(value)
                        else -> viewModel.setCategory(value as String)
                    }
                }
            )
        }
    }
}

@Composable
private fun MedicationCard(item: MedicationItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val med = item.medication
    val usedUp = !med.isActive
    val tone = item.status.tone()
    val barColor = if (usedUp) Cartoon.Grey else tone.fill
    CartoonCard(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(22.dp),
        onClick = onClick,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (usedUp) Cartoon.Ground else categoryTint(med.category))
                    .graphicsLayer { alpha = if (usedUp) 0.5f else 1f }
            ) {
                MedArt(med.packSpec().template.art)
            }
            if (usedUp) InkTag("Used up", Cartoon.Grey, Cartoon.Ink)
            else StatusTag(tone)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            med.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (usedUp) Cartoon.InkMuted else Cartoon.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            med.doseLine(),
            style = MaterialTheme.typography.bodySmall,
            color = Cartoon.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val days = item.daysUntilExpiry
            Text(
                if (days < 0) "${-days}" else "$days",
                style = MaterialTheme.typography.headlineMedium,
                color = if (item.status == ExpiryStatus.EXPIRED && !usedUp) Cartoon.Red else Cartoon.Ink
            )
            Text(
                when {
                    days < 0 -> if (days == -1L) "day ago" else "days ago"
                    days == 1L -> "day"
                    else -> "days"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Cartoon.InkMuted,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        InkProgressBar(fraction = (item.daysUntilExpiry / 365f), color = barColor)
    }
}

@Composable
private fun BottomBar(onNavigate: (HomeTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .cartoon(Cartoon.Paper, RoundedCornerShape(26.dp), shadow = 4.dp)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem(InkIcons.Cabinet, "Cabinet", selected = true) { onNavigate(HomeTab.CABINET) }
        NavItem(InkIcons.Shelves, "Shelf view", selected = false) { onNavigate(HomeTab.SHELF) }
        Box(Modifier.size(66.dp))
        NavItem(InkIcons.Bell, "Alerts", selected = false) { onNavigate(HomeTab.ALERTS) }
        NavItem(InkIcons.Profile, "Settings", selected = false) { onNavigate(HomeTab.SETTINGS) }
    }
    // The scan button floats half above the bar, so it's drawn outside the row's clip.
    Box(modifier.height(72.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .offset(y = (-30).dp)
                .size(66.dp)
                .cartoon(Cartoon.Red, CircleShape, shadow = 4.dp, onClick = { onNavigate(HomeTab.SCAN) })
                .semantics { contentDescription = "Scan medication" },
            contentAlignment = Alignment.Center
        ) {
            Icon(InkIcons.Scan, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun NavItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .then(
                if (selected) Modifier.cartoon(Cartoon.Blue, RoundedCornerShape(16.dp), shadow = 0.dp, onClick = onClick)
                else Modifier.clip(RoundedCornerShape(16.dp)).cartoon(Color.Transparent, RoundedCornerShape(16.dp), 0.dp, border = 0.dp, onClick = onClick)
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (selected) Color.White else Cartoon.Ink, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun EmptyCabinet(onScan: () -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val float = rememberInfiniteTransition(label = "float")
    val bob by float.animateFloat(-6f, 6f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "bob")
    Column(
        modifier = modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(180.dp)
                .cartoon(Cartoon.Blue, CircleShape, shadow = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy((-14).dp), verticalAlignment = Alignment.Bottom) {
                listOf(MedArtKind.BOTTLE, MedArtKind.BOX, MedArtKind.SYRUP).forEachIndexed { i, kind ->
                    MedArt(
                        kind,
                        Modifier.graphicsLayer { translationY = (if (i == 1) -bob else bob) * density },
                        size = 60.dp
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("Your cabinet is empty", style = MaterialTheme.typography.headlineMedium, color = Cartoon.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Scan a pack or type it in. MediCab keeps an eye on expiry dates and nudges you before anything lapses.",
            style = MaterialTheme.typography.bodyMedium,
            color = Cartoon.InkMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        CartoonButton("Scan your first pack", onScan, Modifier.fillMaxWidth(), color = Cartoon.Red, icon = InkIcons.Scan)
        Spacer(Modifier.height(14.dp))
        CartoonButton("Type it in", onAdd, Modifier.fillMaxWidth(), color = Cartoon.Paper, contentColor = Cartoon.Ink, icon = InkIcons.Pencil)
    }
}
