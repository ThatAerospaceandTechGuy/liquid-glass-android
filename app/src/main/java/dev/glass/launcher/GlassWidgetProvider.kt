package dev.glass.launcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.RemoteViews
import java.util.Date

/**
 * 4x2 RemoteViews widget: frosted glass background, white clock, date and
 * battery. Works on any stock launcher; it does not need this app to be the
 * home app.
 *
 * The minute tick and the post-boot re-arm are both handled by this receiver,
 * which is why the app declares zero services.
 */
class GlassWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) updateOne(context, appWidgetManager, id)
    }

    override fun onEnabled(context: Context) {
        GlassClockTicker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        GlassClockTicker.cancel(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        if (context != null && appWidgetManager != null) {
            updateOne(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TICK -> refreshAll(context)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                GlassClockTicker.schedule(context)
                refreshAll(context)
            }
        }
    }

    companion object {

        const val ACTION_TICK = "dev.glass.launcher.action.TICK"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, GlassWidgetProvider::class.java)
            )
            for (id in ids) updateOne(context, manager, id)
        }

        private fun updateOne(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_glass_clock)

            views.setTextViewText(
                R.id.widget_time,
                DateFormat.getTimeFormat(context).format(Date())
            )
            views.setTextViewText(
                R.id.widget_date,
                DateFormat.format("EEE d MMM", System.currentTimeMillis())
            )
            views.setTextViewText(R.id.widget_battery, batteryLabel(context))

            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pending = PendingIntent.getActivity(
                context,
                0x51,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)

            manager.updateAppWidget(widgetId, views)
        }

        /** One sticky broadcast read. No receiver registration, no battery service. */
        private fun batteryLabel(context: Context): String {
            val status = try {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            } catch (t: Throwable) {
                null
            } ?: return "--"
            val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return "--"
            val percent = level * 100 / scale
            val state = status.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = state == BatteryManager.BATTERY_STATUS_CHARGING ||
                state == BatteryManager.BATTERY_STATUS_FULL
            return if (charging) "$percent% \u26A1" else "$percent%"
        }
    }
}

/**
 * Once-a-minute widget refresh with AlarmManager only. Inexact by design: the
 * system batches it, which costs essentially nothing, and a clock that is a few
 * seconds late after a long doze is a fair trade for zero background services.
 */
internal object GlassClockTicker {

    private const val REQUEST_CODE = 0x6C63
    private const val INTERVAL_MS = 60_000L

    fun schedule(context: Context) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()
        val first = now - now % INTERVAL_MS + INTERVAL_MS
        try {
            alarms.setInexactRepeating(
                AlarmManager.RTC,
                first,
                INTERVAL_MS,
                pending(context)
            )
        } catch (t: Throwable) {
            // Some OEMs block repeating alarms; the widget still updates on
            // APPWIDGET_UPDATE and whenever the launcher refreshes it.
        }
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            alarms.cancel(pending(context))
        } catch (t: Throwable) {
            // ignore
        }
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, GlassWidgetProvider::class.java)
            .setAction(GlassWidgetProvider.ACTION_TICK)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
