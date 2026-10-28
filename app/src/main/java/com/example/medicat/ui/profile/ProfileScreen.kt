package com.example.medicat.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.medicat.data.DoseRepository
import com.example.medicat.data.MedicationRepository
import com.example.medicat.data.local.DoseEntity
import com.example.medicat.data.SettingsRepository
import com.example.medicat.domain.ExpiryDates
import com.example.medicat.domain.ExpiryStatus
import com.example.medicat.ui.components.AnimatedCount
import com.example.medicat.ui.components.CartoonButton
import com.example.medicat.ui.components.CartoonTextField
import com.example.medicat.ui.components.FieldLabel
import com.example.medicat.ui.components.InkIcons
import com.example.medicat.ui.components.ScreenHeader
import com.example.medicat.ui.components.SectionHeading
import com.example.medicat.ui.components.cartoon
import com.example.medicat.ui.theme.Cartoon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

data class ProfileUiState(
    val name: String = "",
    val inCabinet: Int = 0,
    val needAttention: Int = 0,
    val usedUp: Int = 0,
    /** Logged doses, newest first. */
    val doses: List<DoseEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val doseRepository: DoseRepository,
    repository: MedicationRepository
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        repository.observeAll(),
        settingsRepository.settings,
        doseRepository.recent(limit = 200)
    ) { meds, settings, doses ->
        val today = LocalDate.now()
        val (active, inactive) = meds.partition { it.isActive }
        ProfileUiState(
            name = settings.profileName,
            inCabinet = active.size,
            needAttention = active.count { ExpiryDates.statusOf(it.expiryDate, settings.reminderDays, today) != ExpiryStatus.SAFE },
            usedUp = inactive.size,
            doses = doses,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun deleteDose(id: Long) {
        viewModelScope.launch { doseRepository.delete(id) }
    }

    fun saveName(name: String) {
        viewModelScope.launch { settingsRepository.setProfileName(name) }
    }
}

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf<String?>(null) }
    // Start editing from the saved name once it has loaded.
    LaunchedEffect(state.isLoading) { if (!state.isLoading && draft == null) draft = state.name }
    val text = draft.orEmpty()
    val changed = text.trim() != state.name
    val focus = LocalFocusManager.current
    fun save() {
        viewModel.saveName(text)
        focus.clearFocus()
    }

    Column(Modifier.fillMaxSize().background(Cartoon.Ground)) {
        ScreenHeader("Profile", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Avatar(text.ifBlank { state.name }, Modifier.align(Alignment.CenterHorizontally))
            Text(
                text.trim().ifBlank { "Add your name" },
                style = MaterialTheme.typography.headlineMedium,
                color = if (text.isBlank()) Cartoon.InkMuted else Cartoon.Ink,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("Your name")
                CartoonTextField(
                    value = text,
                    onValueChange = { draft = it.take(40) },
                    placeholder = "e.g. Adriaan",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() })
                )
                Text(
                    "Used to greet you on the home screen. It stays on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cartoon.InkMuted
                )
            }
            AnimatedVisibility(changed, enter = fadeIn(), exit = fadeOut()) {
                CartoonButton("Save name", ::save, Modifier.fillMaxWidth(), icon = InkIcons.Check, trailingIcon = true)
            }

            SectionHeading("Your cabinet", Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("In cabinet", state.inCabinet, Cartoon.Blue, Color.White, Modifier.weight(1f))
                Stat("Need a look", state.needAttention, Cartoon.Pink, Cartoon.Ink, Modifier.weight(1f))
                Stat("Used up", state.usedUp, Cartoon.Paper, Cartoon.Ink, Modifier.weight(1f))
            }

            SectionHeading("Dose history", Modifier.padding(top = 8.dp))
            DoseHistory(state.doses, onDelete = viewModel::deleteDose)

            SectionHeading("More", Modifier.padding(top = 8.dp))
            LinkRow(InkIcons.Bell, "Reminders & settings", "Alert timing, online lookups, privacy", onOpenSettings)
        }
    }
}

/** Big round badge with the user's initials, or the logo capsule when there's no name yet. */
@Composable
private fun Avatar(name: String, modifier: Modifier = Modifier) {
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }
    Box(
        modifier.size(112.dp).cartoon(Cartoon.Blue, CircleShape, shadow = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        if (initials.isNotEmpty()) {
            Text(initials, style = MaterialTheme.typography.displayMedium, color = Color.White)
        } else {
            Icon(InkIcons.Profile, null, tint = Color.White, modifier = Modifier.size(52.dp))
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, fill: Color, content: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .cartoon(fill, RoundedCornerShape(20.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedCount(value, MaterialTheme.typography.headlineLarge, color = content)
        Text(label, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .cartoon(Cartoon.Paper, RoundedCornerShape(20.dp), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(42.dp)
                .background(Cartoon.BlueSoft, RoundedCornerShape(14.dp))
                .border(2.5.dp, Cartoon.Ink, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Cartoon.Ink, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Cartoon.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Cartoon.InkMuted)
        }
        Icon(InkIcons.Forward, null, tint = Cartoon.Ink, modifier = Modifier.size(20.dp))
    }
}

/** Logged doses grouped by day, newest first; the most recent days start open. */
@Composable
private fun DoseHistory(doses: List<DoseEntity>, onDelete: (Long) -> Unit) {
    if (doses.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().cartoon(Cartoon.Paper, RoundedCornerShape(20.dp)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("No doses logged yet", style = MaterialTheme.typography.titleMedium, color = Cartoon.Ink)
            Text(
                "Open a medication and tap Log a dose. Every dose shows up here.",
                style = MaterialTheme.typography.bodySmall,
                color = Cartoon.InkMuted,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    val zone = remember { ZoneId.systemDefault() }
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val dayFormat = remember { DateTimeFormatter.ofPattern("EEEE d MMMM") }
    val today = LocalDate.now(zone)
    var showAll by rememberSaveable { mutableStateOf(false) }
    val byDay = doses.groupBy { Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate() }.toList()
    val shown = if (showAll) byDay else byDay.take(3)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        shown.forEach { (day, entries) ->
            Column(
                Modifier.fillMaxWidth().cartoon(Cartoon.Paper, RoundedCornerShape(20.dp)).padding(vertical = 6.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when (day) {
                            today -> "Today"
                            today.minusDays(1) -> "Yesterday"
                            else -> day.format(dayFormat)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Cartoon.Ink,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (entries.size == 1) "1 dose" else "${entries.size} doses",
                        style = MaterialTheme.typography.labelMedium,
                        color = Cartoon.InkMuted
                    )
                }
                entries.forEach { dose ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            Instant.ofEpochMilli(dose.takenAt).atZone(zone).format(timeFormat),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier
                                .background(Cartoon.Blue, RoundedCornerShape(10.dp))
                                .border(2.dp, Cartoon.Ink, RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(dose.name, style = MaterialTheme.typography.bodyLarge, color = Cartoon.Ink, maxLines = 1)
                            if (dose.strength.isNotBlank()) {
                                Text(dose.strength, style = MaterialTheme.typography.bodySmall, color = Cartoon.InkMuted)
                            }
                        }
                        IconButton(onClick = { onDelete(dose.id) }) {
                            Icon(InkIcons.Trash, "Delete dose of ${dose.name}", tint = Cartoon.InkMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        if (byDay.size > 3) {
            CartoonButton(
                if (showAll) "Show recent days" else "Show all ${byDay.size} days",
                { showAll = !showAll },
                Modifier.fillMaxWidth(),
                color = Cartoon.Paper,
                contentColor = Cartoon.Ink,
                height = 48.dp
            )
        }
    }
}

