package app.liquidglass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.liquidglass.sys.SystemTools
import app.liquidglass.util.Backdrop

@Composable
fun ControlCenter(
    backdrop: Backdrop?,
    battery: Int,
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    onBrightness: (Float) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var brightness by remember { mutableFloatStateOf(SystemTools.currentBrightness(context)) }
    var volume by remember { mutableFloatStateOf(SystemTools.currentVolume(context)) }
    var focusOn by remember { mutableStateOf(SystemTools.isDndActive(context)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.22f))
            .glassClick(onClose)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.84f)
                .padding(horizontal = 12.dp)
                .glassClick { }
        ) {
            GlassBox(
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                cornerRadius = 40.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Control Centre",
                            color = GlassColors.TextDim,
                            fontSize = 12.sp,
                            letterSpacing = 0.6.sp
                        )
                        Spacer(Modifier.weight(1f))
                        BatteryPill(level = battery)
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f))
                                .glassClick(onClose),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ControlTile(
                            backdrop = backdrop,
                            icon = Icons.Rounded.Wifi,
                            label = "Wi-Fi",
                            active = false,
                            modifier = Modifier.weight(1f)
                        ) { SystemTools.openWifi(context) }

                        ControlTile(
                            backdrop = backdrop,
                            icon = Icons.Rounded.Bluetooth,
                            label = "Bluetooth",
                            active = false,
                            modifier = Modifier.weight(1f)
                        ) { SystemTools.openBluetooth(context) }

                        ControlTile(
                            backdrop = backdrop,
                            icon = Icons.Rounded.FlashOn,
                            label = "Torch",
                            active = torchOn,
                            modifier = Modifier.weight(1f)
                        ) { onToggleTorch() }

                        ControlTile(
                            backdrop = backdrop,
                            icon = Icons.Rounded.DoNotDisturbOn,
                            label = "Focus",
                            active = focusOn,
                            modifier = Modifier.weight(1f)
                        ) { focusOn = SystemTools.toggleDnd(context) }
                    }

                    Spacer(Modifier.height(14.dp))

                    GlassBox(
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 26.dp,
                        gloss = 0.6f
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            SliderRow(
                                icon = Icons.Rounded.LightMode,
                                value = brightness
                            ) { value ->
                                brightness = value
                                onBrightness(value)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.10f))
                            )
                            SliderRow(
                                icon = Icons.Rounded.VolumeUp,
                                value = volume
                            ) { value ->
                                volume = value
                                SystemTools.setVolume(context, value)
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickAction(Icons.Rounded.Settings, "Settings", Modifier.weight(1f)) {
                            SystemTools.openSettings(context)
                        }
                        QuickAction(Icons.Rounded.Wallpaper, "Wallpaper", Modifier.weight(1f)) {
                            SystemTools.openWallpaper(context)
                        }
                        QuickAction(Icons.Rounded.PhotoCamera, "Camera", Modifier.weight(1f)) {
                            SystemTools.openCamera(context)
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Text(
                        text = "Brightness applies to this screen · volume is system-wide",
                        color = GlassColors.TextFaint,
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ControlTile(
    backdrop: Backdrop?,
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassBox(
        backdrop = backdrop,
        modifier = modifier
            .aspectRatio(1f)
            .glassClick(onClick),
        cornerRadius = 26.dp,
        tint = if (active) Color.White.copy(alpha = 0.9f) else Color(0x1AFFFFFF),
        gloss = if (active) 0f else 1f
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color(0xFF101018) else Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                color = if (active) Color(0xFF101018) else GlassColors.TextDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassBox(
        backdrop = null,
        modifier = modifier
            .height(56.dp)
            .glassClick(onClick),
        cornerRadius = 22.dp,
        tint = Color(0x1AFFFFFF),
        gloss = 0.5f
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(text = label, color = GlassColors.TextDim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SliderRow(icon: ImageVector, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GlassColors.TextDim,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White.copy(alpha = 0.85f),
                inactiveTrackColor = Color.White.copy(alpha = 0.22f)
            )
        )
    }
}
