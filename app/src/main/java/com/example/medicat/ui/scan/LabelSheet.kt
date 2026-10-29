package com.example.medicat.ui.scan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.medicat.domain.ExpiryDates
import com.example.medicat.ui.components.CartoonButton
import com.example.medicat.ui.components.InkIcons
import com.example.medicat.ui.components.InkWidth
import com.example.medicat.ui.components.MedArt
import com.example.medicat.ui.components.cartoon
import com.example.medicat.ui.components.stockUnit
import com.example.medicat.ui.pack.Pack3D
import com.example.medicat.ui.pack.PackTemplate
import com.example.medicat.ui.pack.packSpec
import com.example.medicat.ui.theme.Cartoon

/** Bottom sheet in label mode: pack type, what has been read, and the 3D pack it will make. */
@Composable
fun LabelSheet(
    state: LabelScanState,
    onChooseTemplate: (PackTemplate?) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Cartoon.Ink, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .padding(top = InkWidth)
            .background(Cartoon.Paper, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .navigationBarsPadding()
            .padding(top = 10.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 44.dp, height = 6.dp).background(Cartoon.Ink, CircleShape))
        val template = state.template
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (template == null) "What kind of pack is it?" else "It's a ${template.label.lowercase()}",
                style = MaterialTheme.typography.headlineSmall,
                color = Cartoon.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (template != null) {
                SmallLink("Change", InkIcons.Pencil) { onChooseTemplate(null) }
            }
            if (state.hasAnything) SmallLink("Start over", InkIcons.Reset, onReset)
        }

        if (template == null) {
            TemplatePicker(onChooseTemplate)
        }

        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (template != null) ModelPreview(state, template)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val f = state.fields
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FoundField("Name", f.name, Modifier.weight(1.3f))
                FoundField("Strength", f.strength, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FoundField(
                    "Pack size",
                    f.quantity?.let { "$it ${stockUnit(f.form.orEmpty(), it)}".removeSuffix(" left") },
                    Modifier.weight(1f)
                )
                FoundField("Expiry", f.expiry?.let { ExpiryDates.format(ExpiryDates.toMillis(it)) }, Modifier.weight(1.3f))
            }
        }
        }

        CartoonButton(
            text = "Use these",
            onClick = onDone,
            icon = InkIcons.Check,
            trailingIcon = true,
            enabled = state.hasAnything || template != null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        )
    }
}

@Composable
private fun SmallLink(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = Cartoon.InkMuted, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Cartoon.InkMuted)
    }
}

/** Tiles for each 3D template, so the user says what they're holding. */
@Composable
fun TemplatePicker(onChoose: (PackTemplate) -> Unit, selected: PackTemplate? = null, sidePadding: Dp = 20.dp) {
    // Open with the current choice in view.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = ((selected?.ordinal ?: 0) - 1).coerceAtLeast(0))
    // And keep it in view when it's chosen for the user, e.g. by picking a listed medicine.
    LaunchedEffect(selected) { selected?.let { listState.animateScrollToItem((it.ordinal - 1).coerceAtLeast(0)) } }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(start = sidePadding, end = sidePadding, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(PackTemplate.entries) { template ->
            val isSelected = template == selected
            Column(
                Modifier
                    .width(92.dp)
                    .height(104.dp)
                    .cartoon(
                        if (isSelected) Cartoon.BlueSoft else Cartoon.Paper,
                        RoundedCornerShape(20.dp),
                        shadow = if (isSelected) 4.dp else 3.dp,
                        onClick = { onChoose(template) }
                    )
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                MedArt(template.art, size = 56.dp)
                Text(
                    template.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Cartoon.Ink,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}

/** The chosen template printed with the values read so far, turning slowly. */
@Composable
private fun ModelPreview(state: LabelScanState, template: PackTemplate) {
    val f = state.fields
    val spec = packSpec(
        pack = template.name,
        name = f.name.orEmpty(),
        strength = f.strength.orEmpty(),
        form = f.form.orEmpty(),
        category = f.category.orEmpty(),
        expiryMillis = f.expiry?.let(ExpiryDates::toMillis)
    )
    val spin = rememberInfiniteTransition(label = "preview")
    val yaw by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "yaw")
    Pack3D(spec, yaw = yaw - 30f, pitch = 14f, maxWidth = 84.dp, maxHeight = 92.dp, floor = 16.dp, naturalHeight = false)
}

@Composable
private fun FoundField(label: String, value: String?, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(48.dp)
            .background(Cartoon.Ground, RoundedCornerShape(16.dp))
            .border(2.5.dp, Cartoon.Ink, RoundedCornerShape(16.dp))
            .padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedContent(
            targetState = value != null,
            transitionSpec = { scaleIn(spring(0.45f)) togetherWith fadeOut() },
            label = "tick"
        ) { found ->
            Box(
                Modifier
                    .size(24.dp)
                    .background(if (found) Cartoon.Blue else Cartoon.Paper, CircleShape)
                    .border(2.5.dp, if (found) Cartoon.Ink else Cartoon.FoilDash, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (found) Icon(InkIcons.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Cartoon.InkMuted)
            AnimatedContent(targetState = value, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "value") { v ->
                Text(
                    v ?: "Looking…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (v == null) Cartoon.Placeholder else Cartoon.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
