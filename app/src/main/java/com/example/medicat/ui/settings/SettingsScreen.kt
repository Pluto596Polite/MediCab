package com.example.medicat.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.medicat.data.Settings
import com.example.medicat.data.SettingsRepository
import com.example.medicat.data.transfer.CabinetTransfer
import com.example.medicat.data.transfer.ExportFormat
import com.example.medicat.data.transfer.ImportException
import com.example.medicat.notifications.ExpiryNotifier
import com.example.medicat.notifications.ReminderScheduler
import com.example.medicat.ui.components.CartoonButton
import com.example.medicat.ui.components.CartoonChip
import com.example.medicat.ui.components.CartoonDialog
import com.example.medicat.ui.components.CartoonSwitch
import com.example.medicat.ui.components.CartoonTextField
import com.example.medicat.ui.components.InkIcons
import com.example.medicat.ui.components.InkWidth
import com.example.medicat.ui.components.MediCabLogo
import com.example.medicat.ui.components.ScreenHeader
import com.example.medicat.ui.components.SectionHeading
import com.example.medicat.ui.components.cartoon
import com.example.medicat.ui.theme.Cartoon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val notifier: ExpiryNotifier,
    private val scheduler: ReminderScheduler,
    private val transfer: CabinetTransfer
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun canPostNotifications() = notifier.canPostNotifications()

    fun setReminderDays(days: Int) {
        viewModelScope.launch { settingsRepository.setReminderDays(days) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }
    }

    fun setOnlineLookup(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setOnlineLookup(enabled) }
    }

    fun setReminderTime(time: LocalTime) {
        viewModelScope.launch {
            settingsRepository.setReminderTime(time)
            scheduler.schedule(time, replace = true)
        }
    }

    fun checkNow() = scheduler.runNow()

    /** Writes the cabinet to [uri]; [onDone] gets the message to show. */
    fun export(format: ExportFormat, uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val message = runCatching { transfer.export(format, uri) }.fold(
                onSuccess = { count -> "Exported $count ${if (count == 1) "medication" else "medications"} as ${format.label}" },
                onFailure = { "Couldn't save the file" }
            )
            onDone(message)
        }
    }

    /** Adds what's in [uri] to the cabinet; [onDone] gets the message to show. */
    fun import(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val message = runCatching { transfer.import(uri) }.fold(
                onSuccess = { it.summary() },
                onFailure = { (it as? ImportException)?.message ?: "Couldn't read that file" }
            )
            onDone(message)
        }
    }
}

/** Opens the system "save as" screen for an export, suggesting a dated file name. */
private class CreateExportFile : ActivityResultContract<ExportFormat, Uri?>() {
    override fun createIntent(context: Context, input: ExportFormat): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mimeType)
            .putExtra(Intent.EXTRA_TITLE, "MediCab cabinet ${LocalDate.now()}.${input.extension}")

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data.takeIf { resultCode == Activity.RESULT_OK }
}

/** File types offered when importing; CSV often arrives as plain text or as an Excel type. */
private val ImportTypes = arrayOf(
    "application/json",
    "text/*",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
    "application/octet-stream"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var systemAllowsNotifications by remember { mutableStateOf(viewModel.canPostNotifications()) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showCustomDays by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var exportFormat by rememberSaveable { mutableStateOf(ExportFormat.EXCEL) }
    val toast = { message: String -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    val exportLauncher = rememberLauncherForActivityResult(CreateExportFile()) { uri ->
        uri?.let { viewModel.export(exportFormat, it, toast) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.import(it, toast) }
    }
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        systemAllowsNotifications = viewModel.canPostNotifications()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        systemAllowsNotifications = viewModel.canPostNotifications()
    }

    Column(Modifier.fillMaxSize().background(Cartoon.Ground)) {
        ScreenHeader("Settings", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeading("Reminders")
            SettingCard {
                SettingRow(
                    icon = InkIcons.Bell,
                    tint = Cartoon.PinkSoft,
                    title = "Expiry reminders",
                    subtitle = "A daily nudge when something is close to expiring"
                ) {
                    CartoonSwitch(
                        checked = settings.notificationsEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setNotificationsEnabled(enabled)
                            if (enabled && !systemAllowsNotifications &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            ) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }
                AnimatedVisibility(
                    visible = settings.notificationsEnabled && !systemAllowsNotifications,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    BlockedBanner(onOpenSettings = { context.openNotificationSettings() })
                }
            }
            SettingCard(onClick = { showTimePicker = true }) {
                SettingRow(
                    icon = InkIcons.Clock,
                    tint = Cartoon.BlueSoft,
                    title = "Reminder time",
                    subtitle = "When the daily nudge arrives"
                ) {
                    AnimatedContent(
                        targetState = settings.reminderTime,
                        transitionSpec = {
                            (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                        },
                        label = "time"
                    ) { time ->
                        Text(
                            time.format(timeFormatter),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier
                                .background(Cartoon.Blue, RoundedCornerShape(12.dp))
                                .border(2.5.dp, Cartoon.Ink, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            SettingCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        Text("Warn me ahead by", style = MaterialTheme.typography.titleMedium, color = Cartoon.Ink)
                        Text(
                            "Anything inside this window is marked \"Expiring soon\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = Cartoon.InkMuted
                        )
                    }
                    val isCustom = settings.reminderDays !in Settings.REMINDER_OPTIONS
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Settings.REMINDER_OPTIONS.forEach { days ->
                            CartoonChip("${days}d", settings.reminderDays == days, { viewModel.setReminderDays(days) })
                        }
                        CartoonChip(
                            if (isCustom) "${settings.reminderDays}d" else "Custom",
                            isCustom,
                            { showCustomDays = true },
                            selectedColor = Cartoon.Red
                        )
                    }
                }
            }
            SettingCard {
                SettingRow(
                    icon = InkIcons.Flash,
                    tint = Cartoon.YellowSoft,
                    title = "Run check now",
                    subtitle = "Send today's reminder straight away"
                ) {
                    CartoonButton(
                        "Run",
                        onClick = {
                            viewModel.checkNow()
                            Toast.makeText(context, "Checking your cabinet…", Toast.LENGTH_SHORT).show()
                        },
                        color = Cartoon.Red,
                        height = 44.dp
                    )
                }
            }

            SectionHeading("Your data", Modifier.padding(top = 12.dp))
            SettingCard(onClick = { showExport = true }) {
                SettingRow(
                    icon = InkIcons.Box,
                    tint = Cartoon.YellowSoft,
                    title = "Export cabinet",
                    subtitle = "Save it as Excel, CSV, a JSON backup or a printable PDF"
                )
            }
            SettingCard(onClick = { importLauncher.launch(ImportTypes) }) {
                SettingRow(
                    icon = InkIcons.Cabinet,
                    tint = Cartoon.BlueSoft,
                    title = "Import from a file",
                    subtitle = "Add medications from Excel, CSV or a MediCab backup. Ones already in your cabinet are skipped."
                )
            }

            SectionHeading("Scanning & privacy", Modifier.padding(top = 12.dp))
            SettingCard {
                SettingRow(
                    icon = InkIcons.Globe,
                    tint = Cartoon.BlueSoft,
                    title = "Look things up online",
                    subtitle = "Fills in scanned products and their common uses. Only the barcode number or medication name is sent to Open Products Facts, openFDA, UPCitemdb and Wikipedia."
                ) {
                    CartoonSwitch(settings.onlineLookup, viewModel::setOnlineLookup)
                }
            }
            SettingCard {
                SettingRow(
                    icon = InkIcons.Search,
                    tint = Cartoon.PinkSoft,
                    title = "SA medicine list built in",
                    subtitle = "Name suggestions, label matches and pack shapes come from the Department of Health's database of registered medicines, plus a few popular unregistered products, stored on this phone. Works offline."
                )
            }
            SettingCard {
                SettingRow(
                    icon = InkIcons.Lock,
                    tint = Cartoon.YellowSoft,
                    title = "Your cabinet stays on this phone",
                    subtitle = "No account and no cloud. Your medication list and dates are never uploaded."
                )
            }

            Column(
                Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MediCabLogo(size = 48.dp)
                Text("MediCab 1.0", style = MaterialTheme.typography.bodySmall, color = Cartoon.InkMuted, textAlign = TextAlign.Center)
            }
        }
    }

    if (showTimePicker) {
        ReminderTimeDialog(
            initial = settings.reminderTime,
            onDismiss = { showTimePicker = false },
            onConfirm = {
                viewModel.setReminderTime(it)
                showTimePicker = false
            }
        )
    }
    if (showExport) {
        ExportDialog(
            selected = exportFormat,
            onSelect = { exportFormat = it },
            onDismiss = { showExport = false },
            onConfirm = {
                showExport = false
                exportLauncher.launch(exportFormat)
            }
        )
    }
    if (showCustomDays) {
        CustomDaysDialog(
            initial = settings.reminderDays,
            onDismiss = { showCustomDays = false },
            onConfirm = {
                viewModel.setReminderDays(it)
                showCustomDays = false
            }
        )
    }
}

@Composable
private fun SettingCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().cartoon(Cartoon.Paper, RoundedCornerShape(20.dp), onClick = onClick)) { content() }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(42.dp)
                .background(tint, RoundedCornerShape(14.dp))
                .border(2.5.dp, Cartoon.Ink, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Cartoon.Ink, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Cartoon.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Cartoon.InkMuted)
        }
        trailing?.invoke()
    }
}

@Composable
private fun BlockedBanner(onOpenSettings: () -> Unit) {
    Row(
        Modifier
            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
            .fillMaxWidth()
            .background(Cartoon.Red, RoundedCornerShape(14.dp))
            .border(2.5.dp, Cartoon.Ink, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpenSettings)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "Android is blocking notifications",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        Text("Allow", style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

private fun Context.openNotificationSettings() {
    startActivity(
        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context)
    )
    CartoonDialog(
        title = "Reminder time",
        onDismiss = onDismiss,
        confirmText = "Save",
        onConfirm = { onConfirm(LocalTime.of(state.hour, state.minute)) }
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = Cartoon.Ground,
                    selectorColor = Cartoon.Blue,
                    timeSelectorSelectedContainerColor = Cartoon.Blue,
                    timeSelectorSelectedContentColor = Color.White,
                    timeSelectorUnselectedContainerColor = Cartoon.Ground,
                    periodSelectorSelectedContainerColor = Cartoon.Red,
                    periodSelectorSelectedContentColor = Color.White,
                    periodSelectorBorderColor = Cartoon.Ink
                )
            )
        }
    }
}

@Composable
private fun CustomDaysDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.toString()) }
    val days = text.toIntOrNull()
    val valid = days != null && days in Settings.CUSTOM_DAYS_RANGE
    CartoonDialog(
        title = "Custom window",
        onDismiss = onDismiss,
        confirmText = "Save",
        confirmEnabled = valid,
        onConfirm = { days?.let(onConfirm) }
    ) {
        Text(
            "How many days before expiry should MediCab start warning you?",
            style = MaterialTheme.typography.bodyMedium,
            color = Cartoon.InkMuted
        )
        CartoonTextField(
            value = text,
            onValueChange = { value -> text = value.filter(Char::isDigit).take(3) },
            isError = !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            trailing = { Text("days", style = MaterialTheme.typography.bodyLarge, color = Cartoon.InkMuted, modifier = Modifier.padding(end = 10.dp)) }
        )
        Text("Between 1 and 365", style = MaterialTheme.typography.bodySmall, color = if (valid) Cartoon.InkMuted else Cartoon.Red)
    }
}

@Composable
private fun ExportDialog(selected: ExportFormat, onSelect: (ExportFormat) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    CartoonDialog(title = "Export cabinet", onDismiss = onDismiss, confirmText = "Choose where", onConfirm = onConfirm) {
        ExportFormat.entries.forEach { format ->
            val isSelected = format == selected
            Column(
                Modifier
                    .fillMaxWidth()
                    .cartoon(
                        if (isSelected) Cartoon.BlueSoft else Cartoon.Paper,
                        RoundedCornerShape(16.dp),
                        shadow = if (isSelected) 3.dp else 2.dp,
                        onClick = { onSelect(format) }
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text("${format.label} (.${format.extension})", style = MaterialTheme.typography.titleSmall, color = Cartoon.Ink)
                Text(format.description, style = MaterialTheme.typography.bodySmall, color = Cartoon.InkMuted)
            }
        }
    }
}
