package com.example.medicat.ui.update

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.medicat.data.SettingsRepository
import com.example.medicat.data.update.AppRelease
import com.example.medicat.data.update.AppUpdater
import com.example.medicat.ui.components.CartoonDialog
import com.example.medicat.ui.theme.Cartoon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

data class UpdateUiState(
    /** Download progress from 0 to 1 while downloading; null otherwise. */
    val progress: Float? = null,
    /** The downloaded APK, waiting for the user to allow installs or confirm. */
    val downloaded: File? = null,
    val error: String? = null
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updater: AppUpdater,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val offer: StateFlow<AppRelease?> = updater.offer

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    init {
        // Look once per launch, and offer an update at most once a day.
        viewModelScope.launch {
            val settings = settingsRepository.current()
            val today = LocalDate.now().toString()
            if (!updater.isDebugBuild && settings.checkUpdates && settings.updateOfferedOn != today) {
                updater.check()
            }
        }
    }

    fun later() {
        updater.dismissOffer()
        _state.value = UpdateUiState()
        viewModelScope.launch { settingsRepository.setUpdateOfferedOn(LocalDate.now().toString()) }
    }

    fun download(release: AppRelease) {
        if (_state.value.progress != null) return
        _state.value = UpdateUiState(progress = 0f)
        viewModelScope.launch {
            _state.value = runCatching { updater.download(release) { p -> _state.update { it.copy(progress = p) } } }
                .fold(
                    onSuccess = { UpdateUiState(downloaded = it) },
                    onFailure = { UpdateUiState(error = "The download didn't finish. Check your connection and try again.") }
                )
        }
    }

    fun canInstall() = updater.canInstall()
    fun allowInstallsIntent() = updater.allowInstallsIntent()
    fun installIntent(apk: File) = updater.installIntent(apk)
}

/**
 * Offers a newer MediCab release when one is found, downloads it, and opens
 * Android's installer. The first time, Android needs the user to let MediCab
 * install apps; the installer opens by itself when they come back.
 */
@Composable
fun UpdatePrompt(viewModel: UpdateViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val release by viewModel.offer.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val offered = release ?: return

    fun install(apk: File) {
        val intent = if (viewModel.canInstall()) viewModel.installIntent(apk) else viewModel.allowInstallsIntent()
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(viewModel.allowInstallsIntent())
        }
    }
    // Back from "Install unknown apps" with permission granted: carry on.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        state.downloaded?.takeIf { viewModel.canInstall() }?.let(::install)
    }

    val downloading = state.progress != null
    CartoonDialog(
        title = "MediCab ${offered.version} is here",
        onDismiss = { if (!downloading) viewModel.later() },
        confirmText = when {
            downloading -> "Downloading…"
            state.downloaded != null -> "Install"
            state.error != null -> "Try again"
            else -> "Update"
        },
        confirmEnabled = !downloading,
        onConfirm = { state.downloaded?.let(::install) ?: viewModel.download(offered) },
        dismissText = "Later"
    ) {
        Text(
            offered.notes.ifBlank { "A new version of MediCab is ready." },
            style = MaterialTheme.typography.bodyMedium,
            color = Cartoon.Ink,
            modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())
        )
        state.progress?.let { p ->
            LinearProgressIndicator(progress = { p }, color = Cartoon.Blue, trackColor = Cartoon.BlueSoft, modifier = Modifier.fillMaxWidth())
        }
        Text(
            when {
                state.error != null -> state.error!!
                state.downloaded != null && !viewModel.canInstall() ->
                    "Android will ask you to allow MediCab to install apps. Turn it on, then come back."
                else -> "Your cabinet stays as it is. Android will ask you to confirm the update."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (state.error != null) Cartoon.Red else Cartoon.InkMuted
        )
    }
}
