package app.liquidglass.sys

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import kotlin.math.roundToInt

/**
 * Thin wrappers over public system APIs. Everything here is permission-free by design, so the
 * app needs no privileged access and no background service.
 */
object SystemTools {

    private fun launch(context: Context, intent: Intent) {
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            Toast.makeText(context, "Not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    fun openWifi(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        launch(context, intent)
    }

    fun openBluetooth(context: Context) = launch(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    fun openSettings(context: Context) = launch(context, Intent(Settings.ACTION_SETTINGS))

    fun openWallpaper(context: Context) = launch(context, Intent(Intent.ACTION_SET_WALLPAPER))

    fun openCamera(context: Context) = launch(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))

    fun openAppInfo(context: Context, pkg: String) = launch(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
    )

    fun uninstall(context: Context, pkg: String) = launch(
        context,
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
    )

    fun batteryLevel(context: Context): Int {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val direct = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (direct in 0..100) return direct

        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 0
    }

    fun currentBrightness(context: Context): Float {
        val value = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        }.getOrDefault(128)
        return (value / 255f).coerceIn(0.05f, 1f)
    }

    fun currentVolume(context: Context): Float {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0.5f
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    fun setVolume(context: Context, fraction: Float) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val value = (fraction.coerceIn(0f, 1f) * max).roundToInt().coerceIn(0, max)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0) }
    }

    fun isDndActive(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }

    /**
     * Toggling Do Not Disturb needs policy access, which only the user can grant. Without it we
     * send them straight to the grant screen instead of pretending the toggle worked.
     */
    fun toggleDnd(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        if (!manager.isNotificationPolicyAccessGranted) {
            launch(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return isDndActive(context)
        }
        val target = if (manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }
        runCatching { manager.setInterruptionFilter(target) }
        return isDndActive(context)
    }

    fun wallpaperId(context: Context): Int = runCatching {
        android.app.WallpaperManager.getInstance(context).getWallpaperId(
            android.app.WallpaperManager.FLAG_SYSTEM
        )
    }.getOrDefault(0)
}
