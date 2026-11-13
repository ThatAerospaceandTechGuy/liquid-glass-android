package app.liquidglass.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.liquidglass.data.AppEntry
import app.liquidglass.data.AppRepository
import app.liquidglass.sys.SystemTools
import app.liquidglass.sys.Torch
import app.liquidglass.util.Backdrop
import app.liquidglass.util.Imaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun LiquidGlassApp(onBrightness: (Float) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var dockApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var showControl by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }

    val torch = remember { Torch(context) }
    var torchOn by remember { mutableStateOf(false) }
    val battery = rememberBatteryLevel()

    var wallpaperId by remember { mutableIntStateOf(SystemTools.wallpaperId(context)) }

    LaunchedEffect(Unit) {
        val loaded = AppRepository.load(context)
        apps = loaded
        dockApps = AppRepository.pickDock(loaded)
    }

    // Rebuild the frosted backdrop only when the wallpaper actually changed.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                wallpaperId = SystemTools.wallpaperId(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(GlassColors.Base)
    ) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight

        val backdrop by produceState<Backdrop?>(
            initialValue = null,
            widthPx,
            heightPx,
            wallpaperId
        ) {
            if (widthPx > 0 && heightPx > 0) {
                value = withContext(Dispatchers.Default) {
                    Imaging.buildBackdrop(context, widthPx, heightPx)
                }
            }
        }

        val current = backdrop
        if (current != null) {
            Image(
                bitmap = current.sharp,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF191A3A), Color(0xFF07070C))
                        )
                    )
            )
        }

        HomeScreen(
            backdrop = current,
            apps = apps,
            dockApps = dockApps,
            battery = battery,
            onOpenSearch = { showSearch = true },
            onOpenControl = { showControl = true },
            onOpenApp = { app -> app.launch(context) },
            onLongPressApp = { app -> menuApp = app }
        )

        AnimatedVisibility(
            visible = showControl,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 8 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { -it / 10 }
        ) {
            ControlCenter(
                backdrop = current,
                battery = battery,
                torchOn = torchOn,
                onToggleTorch = {
                    if (torch.toggle()) {
                        torchOn = torch.isOn
                    } else {
                        Toast.makeText(context, "No flashlight on this device", Toast.LENGTH_SHORT).show()
                    }
                },
                onBrightness = onBrightness,
                onClose = { showControl = false }
            )
        }

        AnimatedVisibility(
            visible = showSearch,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(120))
        ) {
            SearchOverlay(
                backdrop = current,
                apps = apps,
                onClose = { showSearch = false }
            )
        }

        val menuTarget = menuApp
        if (menuTarget != null) {
            AppMenu(
                backdrop = current,
                app = menuTarget,
                onDismiss = { menuApp = null }
            )
        }
    }

    BackHandler(enabled = showControl || showSearch || menuApp != null) {
        if (menuApp != null) {
            menuApp = null
        } else if (showSearch) {
            showSearch = false
        } else {
            showControl = false
        }
    }
}

@Composable
private fun rememberBatteryLevel(): Int {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(SystemTools.batteryLevel(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            level = SystemTools.batteryLevel(context)
            delay(60_000L)
        }
    }
    return level
}
