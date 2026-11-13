package dev.glass.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import kotlin.math.min

/** One launchable activity. Four fields plus a derived Intent, nothing more. */
class AppEntry(
    val pkg: String,
    val info: ActivityInfo,
    val label: String,
    val key: String
) {
    val intent: Intent
        get() = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(pkg, info.name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

/** PackageManager scan, off the main thread, no allocator churn. */
object AppLoader {

    @Suppress("DEPRECATION")
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            pm.queryIntentActivities(query, 0)
        } catch (t: Throwable) {
            emptyList()
        }
        val self = context.packageName
        val out = ArrayList<AppEntry>(resolved.size)
        for (info in resolved) {
            val activity = info.activityInfo ?: continue
            if (activity.packageName == self) continue
            val label = try {
                info.loadLabel(pm).toString()
            } catch (t: Throwable) {
                activity.packageName
            }
            out.add(
                AppEntry(
                    activity.packageName,
                    activity,
                    label,
                    activity.packageName + "/" + activity.name
                )
            )
        }
        out.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        return out
    }
}

/**
 * A single flat View per app: the glass tile, the icon and the label are all
 * drawn by this one onDraw, so a 20 cell grid is 20 views total. The cell never
 * owns a Bitmap: it asks the shared IconStore at draw time, which is what makes
 * "release everything in onTrimMemory" trivially correct.
 */
class GlassIconView(
    context: Context,
    private val icons: IconStore,
    private val prefs: GlassPrefs,
    private val showLabel: Boolean
) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val glass = LiquidGlassDrawable(density, prefs).apply { shadowDp = 4f }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xF2FFFFFF.toInt()
        textSize = 11f * context.resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val iconRect = RectF()
    private var entry: AppEntry? = null
    private var label: String = ""
    private var tileSize = 0

    init {
        isClickable = true
        isFocusable = false
    }

    fun bind(app: AppEntry) {
        entry = app
        label = app.label
        contentDescription = app.label
        // A recycled cell may have been recycled mid-press; never keep the dip.
        scaleX = 1f
        scaleY = 1f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val labelSpace = if (showLabel) dp(17) else 0
        tileSize = min(width - dp(2), height - labelSpace).coerceAtLeast(dp(28))
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        val app = entry ?: return
        val left = (width - tileSize) / 2
        glass.setBounds(left, 0, left + tileSize, tileSize)
        glass.sync()
        glass.draw(canvas)

        val bitmap: Bitmap? = icons.peek(app.key)
        if (bitmap != null && !bitmap.isRecycled) {
            val pad = tileSize * 0.17f
            iconRect.set(
                left + pad,
                pad,
                left + tileSize - pad,
                tileSize - pad
            )
            canvas.drawBitmap(bitmap, null, iconRect, iconPaint)
        } else {
            icons.request(app) { invalidate() }
        }

        if (showLabel && label.isNotEmpty()) {
            val shown = TextUtils.ellipsize(
                label,
                labelPaint,
                (width - dp(6)).toFloat(),
                TextUtils.TruncateAt.END
            )
            canvas.drawText(shown.toString(), width / 2f, (height - dp(2)).toFloat(), labelPaint)
        }
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val scale = if (pressed) 0.92f else 1f
        scaleX = scale
        scaleY = scale
    }

    private fun dp(value: Int): Int = (value * density + 0.5f).toInt()
}

/**
 * Plain BaseAdapter over a plain GridView. No RecyclerView, no DiffUtil: the
 * list is rebuilt only on first load and when the search text changes.
 * GridView recycles convertView, so scrolling the drawer allocates nothing.
 */
class AppAdapter(
    private val context: Context,
    private val icons: IconStore,
    private val prefs: GlassPrefs,
    private val showLabels: Boolean,
    private val cellHeightDp: Int,
    private val onLaunch: (AppEntry) -> Unit
) : BaseAdapter() {

    private val items = ArrayList<AppEntry>(64)
    private val density = context.resources.displayMetrics.density

    fun setItems(list: List<AppEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): AppEntry = items[position]

    override fun getItemId(position: Int): Long = items[position].key.hashCode().toLong()

    override fun hasStableIds(): Boolean = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView as? GlassIconView ?: GlassIconView(
            context,
            icons,
            prefs,
            showLabels
        ).also {
            it.layoutParams = AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (cellHeightDp * density + 0.5f).toInt()
            )
        }
        val app = items[position]
        view.bind(app)
        view.setOnClickListener { onLaunch(app) }
        return view
    }
}
