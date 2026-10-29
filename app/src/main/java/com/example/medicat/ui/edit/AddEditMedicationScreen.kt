package com.example.medicat.ui.edit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medicat.data.lookup.SaMedicine
import com.example.medicat.data.lookup.UsesNote
import com.example.medicat.domain.ExpiryDates
import com.example.medicat.domain.ExpiryStatus
import com.example.medicat.ui.components.CartoonButton
import com.example.medicat.ui.components.CartoonChip
import com.example.medicat.ui.components.CartoonTextField
import com.example.medicat.ui.components.FieldError
import com.example.medicat.ui.components.FieldLabel
import com.example.medicat.ui.components.InkIcons
import com.example.medicat.ui.components.InkWidth
import com.example.medicat.ui.components.MedArt
import com.example.medicat.ui.components.MedArtKind
import com.example.medicat.ui.components.MedicationForms
import com.example.medicat.ui.components.ScreenHeader
import com.example.medicat.ui.components.Shake
import com.example.medicat.ui.components.StatusTag
import com.example.medicat.ui.components.StorageLocations
import com.example.medicat.ui.components.autoFillFlash
import com.example.medicat.ui.components.cartoon
import com.example.medicat.ui.components.medArtFor
import com.example.medicat.ui.components.stockUnit
import com.example.medicat.ui.pack.Pack3D
import com.example.medicat.ui.pack.PackSpec
import com.example.medicat.ui.pack.PackTemplate
import com.example.medicat.ui.pack.packSpec
import com.example.medicat.ui.scan.TemplatePicker
import com.example.medicat.ui.theme.Cartoon
import com.example.medicat.ui.theme.tone

private val CategorySuggestions = listOf(
    "Pain relief", "Inflammation", "Allergy", "Cold & flu", "Digestive", "First aid",
    "Prescription", "Vitamins", "Skin care"
)

private const val OTHER = "Other"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditMedicationScreen(
    scanResult: String?,
    onScanResultConsumed: () -> Unit,
    onScan: () -> Unit,
    onDone: () -> Unit,
    viewModel: AddEditMedicationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(scanResult) {
        if (scanResult != null) {
            viewModel.onBarcodeScanned(scanResult)
            onScanResultConsumed()
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }
    // After a scan without an expiry in the code, ask for the date right away;
    // the online lookup fills the rest of the form in the meantime.
    LaunchedEffect(state.expiryPromptTick) {
        if (state.expiryPromptTick > 0) showDatePicker = true
    }
    val flash = autoFillFlash(state.autoFillTick)
    LaunchedEffect(state.isDone) {
        if (state.isDone) onDone()
    }

    Box(Modifier.fillMaxSize().background(Cartoon.Ground)) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                if (state.isEditing) "Edit medication" else "Add manually",
                onBack = onDone,
                backIcon = if (state.isEditing) InkIcons.Back else InkIcons.Close,
                backLabel = if (state.isEditing) "Back" else "Cancel"
            )
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Cartoon.Blue, strokeWidth = 4.dp)
                }
                return@Column
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SnapCard(
                    barcode = state.barcode,
                    lookingUp = state.lookingUp,
                    filledFrom = state.filledFrom,
                    spec = packSpec(
                        state.pack, state.name, state.strength, state.form, state.category, state.expiryDate,
                        state.reminderDays, state.barcode.ifBlank { null }
                    ),
                    art = if (state.form.isBlank() && state.category.isBlank()) MedArtKind.BOX else medArtFor(state.form, state.category),
                    onScan = onScan
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("Medication name")
                    Shake(state.errorTick, enabled = state.nameError) { shake ->
                        CartoonTextField(
                            value = state.name,
                            onValueChange = viewModel::onNameChange,
                            placeholder = if (state.lookingUp) "Looking it up…" else "e.g. Ibuprofen",
                            isError = state.nameError,
                            fill = flash,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = {
                                viewModel.dismissSuggestions()
                                defaultKeyboardAction(ImeAction.Next)
                            }),
                            modifier = shake.fillMaxWidth(),
                            trailing = if (state.lookingUp) {
                                {
                                    CircularProgressIndicator(
                                        Modifier.padding(end = 10.dp).size(20.dp),
                                        color = Cartoon.Blue,
                                        strokeWidth = 3.dp
                                    )
                                }
                            } else null
                        )
                    }
                    if (state.nameError) FieldError("Give it a name")
                    MedicineSuggestions(state.suggestions, viewModel::onSuggestionPicked)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel("Strength")
                        CartoonTextField(
                            value = state.strength,
                            onValueChange = viewModel::onStrengthChange,
                            placeholder = "200 mg",
                            fill = flash,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel("Pack size")
                        QuantityStepper(state.quantity, viewModel::onQuantityChange)
                    }
                }

                ChipGroup("Form", MedicationForms, state.form, viewModel::onFormChange)
                CategoryPicker(state.category, viewModel::onCategoryChange)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("Expiry date")
                    Shake(state.errorTick, enabled = state.expiryError) { shake ->
                        ExpiryField(state.expiryDate, state.expiryError, { showDatePicker = true }, shake)
                    }
                    if (state.expiryError) FieldError("Pick an expiry date")
                    ExpiryPreview(state.expiryDate, state.reminderDays)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("3D pack")
                    TemplatePicker(
                        onChoose = { viewModel.onPackChange(it.name) },
                        selected = PackTemplate.of(state.pack, state.form, state.category),
                        sidePadding = 0.dp
                    )
                }

                ChipGroup("Kept in", StorageLocations, state.location, viewModel::onLocationChange)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FieldLabel("Notes", Modifier.weight(1f))
                        if (state.lookingUpUses) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Cartoon.Blue, strokeWidth = 2.5.dp)
                            Text(" Finding uses…", style = MaterialTheme.typography.labelMedium, color = Cartoon.InkMuted)
                        } else if (state.name.isNotBlank()) {
                            TextButton(onClick = viewModel::findUsesNow) {
                                Icon(InkIcons.Globe, null, tint = Cartoon.Blue, modifier = Modifier.size(16.dp))
                                Text(" Find common uses", style = MaterialTheme.typography.labelMedium, color = Cartoon.Blue)
                            }
                        }
                    }
                    CartoonTextField(
                        value = state.description,
                        onValueChange = viewModel::onDescriptionChange,
                        placeholder = "Dosage, who it's for…",
                        singleLine = false,
                        minLines = 3,
                        fill = flash,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                    if (UsesNote.LABEL in state.description) {
                        Text(
                            "Common uses come from public sources and are general information. Always follow the pack leaflet and your pharmacist or doctor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Cartoon.InkMuted
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("Barcode")
                    CartoonTextField(
                        value = state.barcode,
                        onValueChange = viewModel::onBarcodeChange,
                        placeholder = "Scan or type the number",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.onBarcodeScanned(state.barcode) }),
                        trailing = if (state.barcode.length >= 8 && !state.lookingUp) {
                            {
                                // Typed barcodes get the same history + online lookup as scanned ones.
                                TextButton(onClick = { viewModel.onBarcodeScanned(state.barcode) }) {
                                    Text("Look up", style = MaterialTheme.typography.labelMedium, color = Cartoon.Blue)
                                }
                            }
                        } else null
                    )
                }
            }

            CartoonButton(
                text = if (state.isEditing) "Save changes" else "Save to cabinet",
                onClick = viewModel::save,
                icon = InkIcons.Check,
                trailingIcon = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
            )
        }

        SnackbarHost(
            snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp)
        ) { data ->
            Snackbar(
                data,
                shape = RoundedCornerShape(16.dp),
                containerColor = Cartoon.Ink,
                contentColor = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.expiryDate)
        val colors = DatePickerDefaults.colors(
            containerColor = Cartoon.Paper,
            selectedDayContainerColor = Cartoon.Blue,
            todayDateBorderColor = Cartoon.Red,
            todayContentColor = Cartoon.Red,
            selectedYearContainerColor = Cartoon.Blue
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            shape = RoundedCornerShape(28.dp),
            colors = colors,
            modifier = Modifier.border(BorderStroke(InkWidth, Cartoon.Ink), RoundedCornerShape(28.dp)),
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onExpiryChange(pickerState.selectedDateMillis)
                    showDatePicker = false
                }) { Text("Done", style = MaterialTheme.typography.labelLarge, color = Cartoon.Blue) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge, color = Cartoon.Ink)
                }
            }
        ) {
            DatePicker(
                state = pickerState,
                colors = colors,
                title = {
                    AnimatedContent(
                        targetState = when {
                            state.lookingUp -> "Looking up the product…"
                            state.name.isNotBlank() -> "When does ${state.name} expire?"
                            else -> "Expiry date"
                        },
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "pickerTitle",
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                    ) { Text(it, style = MaterialTheme.typography.titleMedium, maxLines = 2) }
                }
            )
        }
    }
}

private enum class ScanState { IDLE, LOOKING_UP, LINKED }

/** Dashed "Snap the box" card that opens the scanner. */
@Composable
private fun SnapCard(
    barcode: String,
    lookingUp: Boolean,
    filledFrom: String?,
    spec: PackSpec,
    art: MedArtKind,
    onScan: () -> Unit
) {
    val scanState = when {
        lookingUp -> ScanState.LOOKING_UP
        barcode.isNotBlank() || filledFrom != null -> ScanState.LINKED
        else -> ScanState.IDLE
    }
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Cartoon.Paper)
            .drawBehind {
                val w = InkWidth.toPx()
                drawRoundRect(
                    Cartoon.Ink,
                    topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(20.dp.toPx() - w / 2),
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())))
                )
            }
            .clickable(role = Role.Button, onClick = onScan)
            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(64.dp).background(Cartoon.BlueSoft, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            // The medication's 3D pack as it'll appear in the cabinet, turning slowly.
            val turn = rememberInfiniteTransition(label = "turn")
            val yaw by turn.animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "yaw")
            Pack3D(spec, yaw = yaw - 30f, pitch = 14f, maxWidth = 60.dp, maxHeight = 50.dp, floor = 12.dp, naturalHeight = false)
        }
        AnimatedContent(
            targetState = scanState,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.94f)) togetherWith fadeOut() },
            label = "snap",
            modifier = Modifier.weight(1f)
        ) { current ->
            Column {
                Text(
                    when (current) {
                        ScanState.LOOKING_UP -> "Looking it up…"
                        ScanState.LINKED -> when {
                            filledFrom != null -> "Details filled in!"
                            else -> "Barcode linked"
                        }
                        ScanState.IDLE -> "Snap the box"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Cartoon.Ink
                )
                Text(
                    when (current) {
                        ScanState.LOOKING_UP -> "Pick the expiry date while we search"
                        ScanState.LINKED -> filledFrom?.let { "From $it · tap to rescan" } ?: "Tap to scan again"
                        ScanState.IDLE -> "Reads the label and fills this in for you"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Cartoon.InkMuted
                )
            }
        }
        val pulse = rememberInfiniteTransition(label = "pulse")
        val s by pulse.animateFloat(1f, 1.1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s")
        Box(
            Modifier
                .size(40.dp)
                .graphicsLayer { if (scanState == ScanState.IDLE) { scaleX = s; scaleY = s } }
                .background(if (scanState == ScanState.LINKED) Cartoon.Blue else Cartoon.Red, CircleShape)
                .border(InkWidth, Cartoon.Ink, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (scanState == ScanState.LINKED) InkIcons.Check else InkIcons.Camera,
                null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Matches from the SA medicine list under the name field; tapping one fills the form. */
@Composable
private fun MedicineSuggestions(suggestions: List<SaMedicine>, onPick: (SaMedicine) -> Unit) {
    AnimatedVisibility(
        visible = suggestions.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        // Keep showing the last matches while the list animates away.
        var shown by remember { mutableStateOf(suggestions) }
        if (suggestions.isNotEmpty()) shown = suggestions
        Column(
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .cartoon(Cartoon.Paper, RoundedCornerShape(16.dp), shadow = 3.dp)
                .padding(vertical = 4.dp)
        ) {
            Text(
                "From the SA medicine list",
                style = MaterialTheme.typography.labelMedium,
                color = Cartoon.InkMuted,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
            shown.forEach { med ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = "Fill in ${med.name}") { onPick(med) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(med.name, style = MaterialTheme.typography.titleSmall, color = Cartoon.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        med.detailLine(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Cartoon.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** "500 mg · 24 tablets · Paracetamol", with "no longer made" for discontinued packs. */
private fun SaMedicine.detailLine(): String {
    val pack = when {
        packSize <= 0 -> null
        form.isBlank() -> "pack of $packSize"
        else -> "$packSize ${stockUnit(form, packSize)}"
    }
    return listOfNotNull(
        strength.takeIf { it.isNotBlank() && " + " !in it },
        pack,
        ingredients.takeIf { it.isNotBlank() },
        "no longer made".takeIf { discontinued }
    ).joinToString(" · ")
}

@Composable
private fun QuantityStepper(value: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier
            .height(52.dp)
            .cartoon(Cartoon.Paper, RoundedCornerShape(16.dp), shadow = 3.dp)
            .padding(horizontal = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperButton(InkIcons.Minus, "One less") { onChange(value - 1) }
        // Typed counts ("24") and the buttons share one value; blank means not tracked.
        var text by remember { mutableStateOf(if (value == 0) "" else value.toString()) }
        LaunchedEffect(value) {
            if ((text.toIntOrNull() ?: 0) != value) text = if (value == 0) "" else value.toString()
        }
        BasicTextField(
            value = text,
            onValueChange = { typed ->
                text = typed.filter(Char::isDigit).take(3)
                onChange(text.toIntOrNull() ?: 0)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(
                color = Cartoon.Ink,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            cursorBrush = SolidColor(Cartoon.Ink),
            modifier = Modifier.width(52.dp).semantics { contentDescription = "Pack size" },
            decorationBox = { field ->
                Box(contentAlignment = Alignment.Center) {
                    if (text.isEmpty()) {
                        Text("–", style = MaterialTheme.typography.titleLarge, color = Cartoon.Placeholder)
                    }
                    field()
                }
            }
        )
        StepperButton(InkIcons.Plus, "One more") { onChange(value + 1) }
    }
}

@Composable
private fun StepperButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Cartoon.Ink, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { option ->
                CartoonChip(option, selected.equals(option, ignoreCase = true), { onSelect(option) })
            }
        }
    }
}

/**
 * Category chips. "Other" opens a field to type your own; a category that
 * isn't one of the chips (e.g. from an online lookup) shows up there too.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPicker(category: String, onChange: (String) -> Unit) {
    val isCustom = category.isNotBlank() && CategorySuggestions.none { it.equals(category, ignoreCase = true) }
    var typing by rememberSaveable { mutableStateOf(isCustom) }
    // A lookup can fill in a custom category after the screen opened.
    LaunchedEffect(isCustom) { if (isCustom) typing = true }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel("Category")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CategorySuggestions.forEach { option ->
                CartoonChip(option, !typing && category.equals(option, ignoreCase = true), {
                    typing = false
                    onChange(if (category.equals(option, ignoreCase = true)) "" else option)
                })
            }
            CartoonChip(OTHER, typing, {
                typing = !typing
                if (!isCustom) onChange("")
            }, selectedColor = Cartoon.Red)
        }
        AnimatedVisibility(typing, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { if (category.isBlank()) focus.requestFocus() }
            CartoonTextField(
                value = category,
                onValueChange = onChange,
                placeholder = "Type the category, e.g. Eye care",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
        }
    }
}

@Composable
private fun ExpiryField(expiryDate: Long?, isError: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .cartoon(
                Cartoon.Paper, RoundedCornerShape(16.dp), shadow = 3.dp,
                borderColor = if (isError) Cartoon.Red else Cartoon.Ink,
                onClick = onClick, onClickLabel = "Pick expiry date"
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            expiryDate?.let(ExpiryDates::format) ?: "Pick a date",
            style = MaterialTheme.typography.bodyLarge,
            color = if (expiryDate == null) Cartoon.Placeholder else Cartoon.Ink,
            modifier = Modifier.weight(1f)
        )
        Icon(InkIcons.Calendar, null, tint = Cartoon.Ink, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ExpiryPreview(expiryDate: Long?, reminderDays: Int) {
    AnimatedVisibility(
        visible = expiryDate != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val millis = expiryDate ?: return@AnimatedVisibility
        val days = ExpiryDates.daysUntil(millis)
        val status = ExpiryDates.statusOf(millis, reminderDays)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            StatusTag(
                status.tone(),
                text = when (status) {
                    ExpiryStatus.SAFE -> "Fresh"
                    ExpiryStatus.EXPIRING_SOON -> "Expiring soon"
                    ExpiryStatus.EXPIRED -> "Expired"
                }
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (days < 0) "Expired ${ExpiryDates.relative(days)}" else "Expires ${ExpiryDates.relative(days)}",
                style = MaterialTheme.typography.bodySmall,
                color = Cartoon.InkMuted
            )
        }
    }
}
