package app.liquidglass.data

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import app.liquidglass.util.Imaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: ImageBitmap
) {
    fun launch(context: Context) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(packageName, activityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        runCatching { context.startActivity(intent) }
    }
}

object AppRepository {

    private const val ICON_PX = 144

    @Volatile
    private var cache: List<AppEntry>? = null

    suspend fun load(context: Context): List<AppEntry> {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            query(context).also { cache = it }
        }
    }

    private fun query(context: Context): List<AppEntry> {
        val manager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching { manager.queryIntentActivities(intent, 0) }
            .getOrElse { emptyList() }

        val seen = HashSet<String>()
        val entries = ArrayList<AppEntry>(resolved.size)
        for (info in resolved) {
            val activity = info.activityInfo ?: continue
            val pkg = activity.packageName ?: continue
            val name = activity.name ?: continue
            if (pkg == context.packageName) continue
            if (!seen.add("$pkg/$name")) continue

            val label = runCatching { info.loadLabel(manager)?.toString() }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: pkg
            val icon = runCatching { Imaging.drawableToImageBitmap(info.loadIcon(manager), ICON_PX) }
                .getOrNull()
                ?: continue

            entries.add(AppEntry(label, pkg, name, icon))
        }

        entries.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        return entries
    }

    /** Picks the most phone-like apps for the dock, then pads with the first apps installed. */
    fun pickDock(apps: List<AppEntry>): List<AppEntry> {
        val preferred = listOf(
            listOf("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer"),
            listOf(
                "com.google.android.apps.messaging",
                "com.android.messaging",
                "com.samsung.android.messaging"
            ),
            listOf(
                "com.android.chrome",
                "org.mozilla.firefox",
                "com.brave.browser",
                "com.microsoft.emmx",
                "com.android.browser"
            ),
            listOf(
                "com.google.android.GoogleCamera",
                "com.android.camera2",
                "com.android.camera",
                "com.samsung.android.camera"
            )
        )

        val dock = ArrayList<AppEntry>(4)
        for (group in preferred) {
            val match = apps.firstOrNull { app -> group.any { it == app.packageName } } ?: continue
            if (dock.none { it.packageName == match.packageName }) dock.add(match)
        }
        for (app in apps) {
            if (dock.size >= 4) break
            if (dock.none { it.packageName == app.packageName }) dock.add(app)
        }
        return dock
    }
}
