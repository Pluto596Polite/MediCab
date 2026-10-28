package com.example.medicat.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.medicat.ui.alerts.AlertsScreen
import com.example.medicat.ui.detail.DetailScreen
import com.example.medicat.ui.edit.AddEditMedicationScreen
import com.example.medicat.ui.gallery.CabinetGalleryScreen
import com.example.medicat.ui.gallery.VirtualCabinetScreen
import com.example.medicat.ui.inventory.HomeTab
import com.example.medicat.ui.inventory.InventoryScreen
import com.example.medicat.ui.scan.ScannerScreen
import com.example.medicat.ui.profile.ProfileScreen
import com.example.medicat.ui.settings.SettingsScreen
import com.example.medicat.ui.splash.SplashOverlay
import com.example.medicat.ui.splash.SplashRevealAt
import com.example.medicat.ui.theme.Cartoon

private object Routes {
    const val INVENTORY = "inventory"
    const val DETAIL = "detail/{id}"
    const val EDIT = "edit?id={id}&code={code}"
    const val SCAN = "scan?standalone={standalone}"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val ALERTS = "alerts"
    const val CABINET = "cabinet"
    const val GALLERY = "gallery?focus={focus}"

    fun detail(id: Int) = "detail/$id"
    fun gallery(focus: Int) = "gallery?focus=$focus"
    fun scan(standalone: Boolean) = "scan?standalone=$standalone"

    fun edit(id: Int? = null, code: String? = null) = buildString {
        append("edit")
        val args = listOfNotNull(id?.let { "id=$it" }, code?.let { "code=${Uri.encode(it)}" })
        if (args.isNotEmpty()) append(args.joinToString("&", prefix = "?"))
    }
}

/** Smooth ease-out used for all screen transitions. */
private val Ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Key used to hand a scanned raw value back to the add/edit screen. */
const val SCAN_RESULT_KEY = "scan_result"

@Composable
fun MediCabNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Launch animation, once per process; skipped when the system has animations off.
    val animationsOff = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    var showSplash by rememberSaveable { mutableStateOf(!animationsOff) }
    var splashProgress by remember { mutableFloatStateOf(if (showSplash) 0f else 1f) }
    // Ask after the splash so the system dialog doesn't cover it.
    if (!showSplash) RequestNotificationPermissionOnce()

    Box(Modifier.fillMaxSize().background(Cartoon.Ground)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Home settles in from slightly zoomed as the splash fades.
                    val settle = ((splashProgress - 0.667f) / (SplashRevealAt - 0.667f)).coerceIn(0f, 1f)
                    val s = 1.05f - 0.05f * settle
                    scaleX = s
                    scaleY = s
                }
        ) {
            AppNavHost(navController)
        }
        if (showSplash) {
            SplashOverlay(
                onProgress = { splashProgress = it },
                onFinished = {
                    splashProgress = 1f
                    showSplash = false
                }
            )
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.INVENTORY,
        modifier = Modifier.background(Cartoon.Ground),
        enterTransition = {
            fadeIn(tween(220, delayMillis = 40)) +
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(360, easing = Ease)) { it / 5 }
        },
        exitTransition = {
            fadeOut(tween(160)) +
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(360, easing = Ease)) { it / 10 }
        },
        popEnterTransition = {
            fadeIn(tween(220, delayMillis = 40)) +
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(360, easing = Ease)) { it / 10 }
        },
        popExitTransition = {
            fadeOut(tween(160)) +
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(360, easing = Ease)) { it / 5 }
        }
    ) {
        composable(Routes.INVENTORY) {
            InventoryScreen(
                onAddMedication = { navController.navigate(Routes.edit()) },
                onOpenMedication = { id -> navController.navigate(Routes.detail(id)) },
                onNavigate = { tab ->
                    when (tab) {
                        HomeTab.CABINET -> Unit
                        HomeTab.SHELF -> navController.navigate(Routes.CABINET)
                        HomeTab.SCAN -> navController.navigate(Routes.scan(standalone = true))
                        HomeTab.ALERTS -> navController.navigate(Routes.ALERTS)
                        HomeTab.PROFILE -> navController.navigate(Routes.PROFILE)
                    }
                }
            )
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) {
            DetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Routes.edit(id)) },
                onView3D = { id -> navController.navigate(Routes.gallery(id)) }
            )
        }
        composable(Routes.ALERTS) {
            AlertsScreen(
                onBack = { navController.popBackStack() },
                onOpenMedication = { id -> navController.navigate(Routes.detail(id)) }
            )
        }
        composable(Routes.CABINET) {
            VirtualCabinetScreen(
                onBack = { navController.popBackStack() },
                onOpenBox = { id -> navController.navigate(Routes.gallery(id)) },
                onAddMedication = { navController.navigate(Routes.edit()) }
            )
        }
        composable(
            route = Routes.GALLERY,
            arguments = listOf(navArgument("focus") { type = NavType.IntType; defaultValue = -1 }),
            enterTransition = { fadeIn(tween(250)) + scaleIn(tween(380, easing = Ease), initialScale = 0.88f) },
            popExitTransition = { fadeOut(tween(200)) + scaleOut(tween(250), targetScale = 0.92f) }
        ) { entry ->
            CabinetGalleryScreen(
                focusId = entry.arguments?.getInt("focus") ?: -1,
                onBack = { navController.popBackStack() },
                onOpenMedication = { id -> navController.navigate(Routes.detail(id)) },
                onAddMedication = { navController.navigate(Routes.edit()) }
            )
        }
        composable(
            route = Routes.EDIT,
            arguments = listOf(
                navArgument("id") { type = NavType.IntType; defaultValue = -1 },
                navArgument("code") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { entry ->
            val scanResult by entry.savedStateHandle
                .getStateFlow<String?>(SCAN_RESULT_KEY, null)
                .collectAsState()
            AddEditMedicationScreen(
                scanResult = scanResult,
                onScanResultConsumed = { entry.savedStateHandle[SCAN_RESULT_KEY] = null },
                onScan = { navController.navigate(Routes.scan(standalone = false)) },
                onDone = { navController.popBackStack() }
            )
        }
        composable(
            Routes.SCAN,
            arguments = listOf(navArgument("standalone") { type = NavType.BoolType; defaultValue = false }),
            enterTransition = { fadeIn(tween(250)) + scaleIn(tween(350, easing = Ease), initialScale = 0.92f) },
            popExitTransition = { fadeOut(tween(200)) + scaleOut(tween(250), targetScale = 0.96f) }
        ) { entry ->
            val standalone = entry.arguments?.getBoolean("standalone") ?: false
            // Started from the home screen, a scan opens a new form; started from the form, it returns to it.
            fun openForm(code: String?) = navController.navigate(Routes.edit(code = code)) {
                popUpTo(Routes.SCAN) { inclusive = true }
            }
            ScannerScreen(
                onScanned = { raw ->
                    if (standalone) openForm(raw)
                    else {
                        navController.previousBackStackEntry?.savedStateHandle?.set(SCAN_RESULT_KEY, raw)
                        navController.popBackStack()
                    }
                },
                // The label scan travels through ScanInbox, so both cases just need the form on screen.
                onLabelDone = { if (standalone) openForm(null) else navController.popBackStack() },
                onBack = { navController.popBackStack() },
                onTypeIn = { if (standalone) openForm(null) else navController.popBackStack() }
            )
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
