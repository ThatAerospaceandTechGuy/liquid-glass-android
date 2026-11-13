package app.liquidglass.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.liquidglass.data.AppEntry
import app.liquidglass.sys.SystemTools
import app.liquidglass.util.Backdrop
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val TimeFormat = DateTimeFormatter.ofPattern("H:mm")
private val DateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM")
private val DayFormat = DateTimeFormatter.ofPattern("EEEE")

@Composable
fun HomeScreen(
    backdrop: Backdrop?,
    apps: List<AppEntry>,
    dockApps: List<AppEntry>,
    battery: Int,
    onOpenSearch: () -> Unit,
    onOpenControl: () -> Unit,
    onOpenApp: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit
) {
    val now = rememberNow()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(6.dp))

        ClockWidget(
            backdrop = backdrop,
            now = now,
            battery = battery,
            onOpenControl = onOpenControl
        )

        Spacer(Modifier.height(14.dp))

        if (apps.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading apps", color = GlassColors.TextFaint, fontSize = 13.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items = apps, key = { "${it.packageName}/${it.activityName}" }) { app ->
                    AppIcon(
                        app = app,
                        onClick = { onOpenApp(app) },
                        onLongClick = { onLongPressApp(app) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        PageDots()
        Spacer(Modifier.height(10.dp))
        SearchPill(backdrop = backdrop, onClick = onOpenSearch)
        Spacer(Modifier.height(12.dp))
        Dock(
            backdrop = backdrop,
            apps = dockApps,
            onOpen = onOpenApp,
            onLongPress = onLongPressApp
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            val millis = System.currentTimeMillis()
            delay(60_000L - (millis % 60_000L) + 50L)
        }
    }
    return now
}

@Composable
private fun ClockWidget(
    backdrop: Backdrop?,
    now: LocalDateTime,
    battery: Int,
    onOpenControl: () -> Unit
) {
    val time = remember(now) { now.format(TimeFormat) }
    val date = remember(now) { now.format(DateFormat) }
    val day = remember(now) { now.format(DayFormat).uppercase() }

    GlassBox(
        backdrop = backdrop,
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        cornerRadius = 30.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = day,
                    color = GlassColors.TextDim,
                    fontSize = 11.sp,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                BatteryPill(level = battery)
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .glassClick(onOpenControl),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = "Control Centre",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = time,
                color = Color.White,
                fontSize = 54.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-2).sp
            )

            Spacer(Modifier.weight(1f))

            Text(text = date, color = GlassColors.TextDim, fontSize = 13.sp)
        }
    }
}

@Composable
fun BatteryPill(level: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.width(22.dp).height(11.dp)) {
            val stroke = 1.dp.toPx()
            val corner = CornerRadius(3.dp.toPx())
            val shell = Color.White.copy(alpha = 0.55f)
            drawRoundRect(color = shell, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke), cornerRadius = corner)
            drawRoundRect(
                color = shell,
                topLeft = Offset(size.width - stroke * 1.2f, size.height * 0.33f),
                size = Size(stroke * 2f, size.height * 0.34f),
                cornerRadius = CornerRadius(stroke * 0.8f)
            )
            val inset = stroke + 1.dp.toPx()
            val fillWidth = ((size.width - inset * 2) * (level.coerceIn(0, 100) / 100f))
                .coerceAtLeast(1.dp.toPx())
            drawRoundRect(
                color = Color.White.copy(alpha = 0.92f),
                topLeft = Offset(inset, inset),
                size = Size(fillWidth, size.height - inset * 2),
                cornerRadius = CornerRadius(1.5.dp.toPx())
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$level%",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppIcon(
    app: AppEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showLabel: Boolean = true
) {
    val iconShape = remember { SquircleShape() }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "iconPress")
    val haptics = LocalHapticFeedback.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onClick = onClick
            )
            .padding(vertical = 2.dp)
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(elevation = 8.dp, shape = iconShape, clip = false)
                .clip(iconShape)
        )
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = app.label,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.96f),
                    shadow = Shadow(Color.Black.copy(alpha = 0.35f), blurRadius = 8f)
                )
            )
        }
    }
}

@Composable
private fun PageDots() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(2) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (index == 0) 6.dp else 5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (index == 0) 0.9f else 0.35f))
            )
        }
    }
}

@Composable
private fun SearchPill(backdrop: Backdrop?, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        GlassBox(
            backdrop = backdrop,
            modifier = Modifier
                .width(168.dp)
                .height(42.dp)
                .glassClick(onClick),
            cornerRadius = 999.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = GlassColors.TextDim,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text("Search", color = GlassColors.TextDim, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Dock(
    backdrop: Backdrop?,
    apps: List<AppEntry>,
    onOpen: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit
) {
    GlassBox(
        backdrop = backdrop,
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp),
        cornerRadius = 34.dp,
        tint = Color(0x1FFFFFFF)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            apps.forEach { app ->
                AppIcon(
                    app = app,
                    onClick = { onOpen(app) },
                    onLongClick = { onLongPress(app) },
                    showLabel = false
                )
            }
        }
    }
}

@Composable
fun AppMenu(backdrop: Backdrop?, app: AppEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.36f))
            .glassClick(onDismiss),
        contentAlignment = Alignment.Center
    ) {
        GlassBox(
            backdrop = backdrop,
            modifier = Modifier
                .width(286.dp)
                .glassClick { },
            cornerRadius = 32.dp,
            tint = Color(0x26FFFFFF)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    bitmap = app.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(remember { SquircleShape() })
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = app.label,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(18.dp))
                MenuRow(Icons.Rounded.Info, "App info") {
                    SystemTools.openAppInfo(context, app.packageName)
                    onDismiss()
                }
                MenuRow(Icons.Rounded.DeleteOutline, "Uninstall") {
                    SystemTools.uninstall(context, app.packageName)
                    onDismiss()
                }
                MenuRow(Icons.Rounded.Close, "Cancel") { onDismiss() }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .glassClick(onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(text = label, color = Color.White, fontSize = 14.sp)
    }
}
