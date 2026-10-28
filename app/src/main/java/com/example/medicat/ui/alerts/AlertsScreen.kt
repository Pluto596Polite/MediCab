package com.example.medicat.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medicat.domain.ExpiryDates
import com.example.medicat.domain.ExpiryStatus
import com.example.medicat.ui.components.CartoonButton
import com.example.medicat.ui.components.CartoonDialog
import com.example.medicat.ui.components.DecorCircle
import com.example.medicat.ui.components.InkIcons
import com.example.medicat.ui.components.MedArt
import com.example.medicat.ui.components.ScreenHeader
import com.example.medicat.ui.components.SectionHeading
import com.example.medicat.ui.components.cartoon
import com.example.medicat.ui.components.doseLine
import com.example.medicat.ui.components.entrance
import com.example.medicat.ui.inventory.InventoryViewModel
import com.example.medicat.ui.inventory.MedicationItem
import com.example.medicat.ui.pack.packSpec
import com.example.medicat.ui.theme.Cartoon
import java.time.format.DateTimeFormatter

@Composable
fun AlertsScreen(
    onBack: () -> Unit,
    onOpenMedication: (Int) -> Unit,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val expired = state.active.filter { it.status == ExpiryStatus.EXPIRED }
    val upcoming = state.active.filter { it.status == ExpiryStatus.EXPIRING_SOON }
    val fresh = state.active.filter { it.status == ExpiryStatus.SAFE }
    var disposing by rememberSaveable { mutableStateOf<String?>(null) }
    val seen = remember { mutableSetOf<Any>() }

    Column(Modifier.fillMaxSize().background(Cartoon.Ground)) {
        ScreenHeader("Alerts", onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().navigationBarsPadding()
        ) {
            if (expired.isNotEmpty()) {
                item(key = "expiredHeading") { SectionHeading("Expired") }
                items(expired, key = { "x" + it.medication.id }) { item ->
                    ExpiredCard(
                        item = item,
                        onOpen = { onOpenMedication(item.medication.id) },
                        onDispose = { disposing = item.medication.name },
                        onRemove = { viewModel.setActive(item.medication, false) },
                        modifier = Modifier.animateItem().entrance(seen.add("x" + item.medication.id))
                    )
                }
            }
            if (upcoming.isNotEmpty()) {
                item(key = "upcomingHeading") {
                    SectionHeading("Coming up", Modifier.padding(top = if (expired.isNotEmpty()) 12.dp else 0.dp))
                }
                items(upcoming, key = { "u" + it.medication.id }) { item ->
                    UpcomingRow(
                        item = item,
                        reminderDays = state.reminderDays,
                        onClick = { onOpenMedication(item.medication.id) },
                        modifier = Modifier.animateItem().entrance(seen.add("u" + item.medication.id), 60)
                    )
                }
            }
            if (!state.isLoading) {
                item(key = "clear") {
                    AllClear(
                        freshCount = fresh.size,
                        nothingDue = expired.isEmpty() && upcoming.isEmpty(),
                        nextFresh = fresh.firstOrNull(),
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .entrance(seen.add("clear"), 120)
                    )
                }
            }
        }
    }

    disposing?.let { name ->
        CartoonDialog(
            title = "Getting rid of $name",
            onDismiss = { disposing = null },
            confirmText = "Got it",
            dismissText = null,
            onConfirm = { disposing = null }
        ) {
            listOf(
                "Take it to a pharmacy. Most will dispose of old medicine safely for free.",
                "Don't flush it or pour it down the sink.",
                "Keep it in its original pack with the label until then."
            ).forEach { tip ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.padding(top = 2.dp).size(22.dp).background(Cartoon.Blue, CircleShape).border(2.dp, Cartoon.Ink, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(InkIcons.Check, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
                    Text(tip, style = MaterialTheme.typography.bodyMedium, color = Cartoon.Ink)
                }
            }
        }
    }
}

@Composable
private fun ExpiredCard(
    item: MedicationItem,
    onOpen: () -> Unit,
    onDispose: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val med = item.medication
    Column(
        modifier
            .fillMaxWidth()
            .cartoon(Cartoon.Red, RoundedCornerShape(24.dp), shadow = 5.dp, onClick = onOpen)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            DaysTile(item.daysUntilExpiry, fill = Cartoon.Paper, numberColor = Cartoon.Red, size = 64)
            Column(Modifier.weight(1f)) {
                Text(med.name, style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.9f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Expired ${ExpiryDates.format(med.expiryDate)} · ${med.doseLine()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 2
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CartoonButton("How to dispose", onDispose, Modifier.weight(1f), color = Cartoon.Paper, contentColor = Cartoon.Ink, height = 46.dp)
            CartoonButton("Remove", onRemove, Modifier.weight(1f), color = Cartoon.Ink, height = 46.dp)
        }
    }
}

@Composable
private fun UpcomingRow(item: MedicationItem, reminderDays: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val days = item.daysUntilExpiry
    // Hotter colour the closer it gets.
    val tile = when {
        days <= 7 -> Cartoon.Coral
        days <= reminderDays / 2 -> Cartoon.PinkLight
        else -> Cartoon.Blush
    }
    Row(
        modifier
            .fillMaxWidth()
            .cartoon(Cartoon.Paper, RoundedCornerShape(22.dp), onClick = onClick)
            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DaysTile(days, fill = tile, numberColor = Cartoon.Ink, size = 56)
        Column(Modifier.weight(1f)) {
            Text(item.medication.name, style = MaterialTheme.typography.titleMedium, color = Cartoon.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(item.medication.doseLine(), item.medication.location).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Cartoon.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        MedArt(item.packSpec().template.art)
    }
}

@Composable
private fun DaysTile(days: Long, fill: Color, numberColor: Color, size: Int) {
    Column(
        Modifier
            .size(size.dp)
            .background(fill, RoundedCornerShape(if (size > 60) 18.dp else 16.dp))
            .border(3.dp, Cartoon.Ink, RoundedCornerShape(if (size > 60) 18.dp else 16.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (days < 0) "$days" else "$days",
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize * (size / 64f)),
            color = numberColor
        )
        Text(if (days == 1L || days == -1L) "day" else "days", style = MaterialTheme.typography.labelSmall, color = Cartoon.Ink)
    }
}

@Composable
private fun AllClear(freshCount: Int, nothingDue: Boolean, nextFresh: MedicationItem?, modifier: Modifier = Modifier) {
    val month = remember { DateTimeFormatter.ofPattern("MMMM") }
    Box(
        modifier
            .fillMaxWidth()
            .cartoon(Cartoon.Blue, RoundedCornerShape(24.dp), shadow = 5.dp)
    ) {
        DecorCircle(Modifier.align(Alignment.BottomEnd).offset(30.dp, 40.dp).size(120.dp), alpha = 0.14f)
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(46.dp).background(Cartoon.Paper, CircleShape).border(3.dp, Cartoon.Ink, CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(InkIcons.Check, null, tint = Cartoon.Blue, modifier = Modifier.size(22.dp)) }
            Column {
                Text(
                    when (freshCount) {
                        0 -> "Nothing fresh yet"
                        1 -> "1 med is fresh"
                        else -> "$freshCount meds are fresh"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.9f),
                    color = Color.White
                )
                val next = nextFresh?.let { ExpiryDates.toLocalDate(it.medication.expiryDate).format(month) }
                Text(
                    when {
                        nothingDue && next != null -> "Nothing to worry about until $next."
                        next != null -> "After these, nothing else until $next."
                        nothingDue -> "No alerts right now."
                        else -> "Sort the ones above and you're all clear."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
        }
    }
}
