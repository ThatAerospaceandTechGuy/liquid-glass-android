package dev.glass.launcher

import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.util.LruCache
import kotlin.math.min

/**
 * The only place in the app that holds pixels.
 *
 * Rules enforced here:
 *  - icons are decoded with BitmapFactory.Options.inSampleSize straight from the
 *    icon resource, then scaled to exactly 48dp, so a full resolution icon is
 *    never retained;
 *  - adaptive/vector icons are rasterised once at 48dp into an ARGB_8888 bitmap;
 *  - all results live in ONE LruCache with a hard 512 KB budget;
 *  - evicted bitmaps are recycled immediately, and nothing outside this class
 *    keeps a Bitmap reference, so `clear()` really frees the pixels.
 */
class IconStore(context: Context) {

    private val app: Context = context.applicationContext
    private val pm = app.packageManager
    private val density = app.resources.displayMetrics.density

    /** Exact 48dp in pixels for this device. */
    val iconPx: Int = (ICON_DP * density + 0.5f).toInt().coerceAtLeast(1)

    private val cache = object : LruCache<String, Bitmap>(ICON_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && oldValue !== newValue && !oldValue.isRecycled) oldValue.recycle()
        }
    }

    private val inFlight = HashSet<String>(8)
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("glass-icons", Process.THREAD_PRIORITY_BACKGROUND)
    private val worker: Handler

    init {
        thread.start()
        worker = Handler(thread.looper)
    }

    fun peek(key: String): Bitmap? = cache.get(key)

    /**
     * Returns a cached bitmap through [peek], otherwise decodes on the icon
     * thread and calls [onReady] on the main thread. Duplicate requests for the
     * same key are coalesced, so calling this from onDraw is safe and free.
     */
    fun request(entry: AppEntry, onReady: () -> Unit) {
        if (cache.get(entry.key) != null) return
        if (!inFlight.add(entry.key)) return
        worker.post {
            val bitmap = try {
                load(entry)
            } catch (t: Throwable) {
                null
            }
            main.post {
                inFlight.remove(entry.key)
                if (bitmap != null && !bitmap.isRecycled) {
                    cache.put(entry.key, bitmap)
                    onReady()
                }
            }
        }
    }

    /**
     * onTrimMemory mapping. UI_HIDDEN drops everything, memory pressure halves
     * the budget. Both are safe: no view ever holds a Bitmap of its own.
     */
    fun trim(level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> cache.evictAll()
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> cache.trimToSize(ICON_CACHE_BYTES / 2)
        }
    }

    fun clear() {
        cache.evictAll()
    }

    fun destroy() {
        cache.evictAll()
        thread.quitSafely()
    }

    private fun load(entry: AppEntry): Bitmap? = decodeResource(entry) ?: decodeDrawable(entry)

    /**
     * Fast path: decode the icon resource itself with inSampleSize so the full
     * size PNG/WebP never enters the heap. Returns null for adaptive icons.
     */
    private fun decodeResource(entry: AppEntry): Bitmap? {
        val info = entry.info
        if (info.icon == 0) return null
        val resources: Resources = try {
            pm.getResourcesForApplication(info.applicationInfo)
        } catch (t: Throwable) {
            return null
        }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resources.openRawResource(info.icon).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, iconPx)
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
            val decoded = resources.openRawResource(info.icon)
                .use { BitmapFactory.decodeStream(it, null, options) } ?: return null
            scaleTo(decoded, iconPx)
        } catch (t: Throwable) {
            null
        }
    }

    /** Adaptive icon (API 26+), vector, or anything the raw decoder cannot read. */
    private fun decodeDrawable(entry: AppEntry): Bitmap? {
        val drawable: Drawable = try {
            pm.getActivityIcon(ComponentName(entry.pkg, entry.info.name))
        } catch (t: Throwable) {
            return null
        }
        return try {
            val bitmap = Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, iconPx, iconPx)
            drawable.draw(canvas)
            bitmap
        } catch (t: Throwable) {
            null
        }
    }

    private fun scaleTo(source: Bitmap, size: Int): Bitmap {
        if (source.width == size && source.height == size) return source
        val scaled = Bitmap.createScaledBitmap(source, size, size, true)
        if (scaled !== source) source.recycle()
        return scaled
    }

    companion object {
        const val ICON_DP = 48

        /**
         * Hard 512 KB cap. At xxhdpi a 48dp icon is 144x144x4 = ~81 KB, so about
         * six icons stay resident; the rest are re-decoded on the icon thread in
         * well under a millisecond. Raise this to 2 MB if you want a longer-lived
         * cache at the cost of heap.
         */
        const val ICON_CACHE_BYTES = 512 * 1024

        /** Largest power-of-two inSampleSize that still yields >= target px. */
        fun sampleSize(width: Int, height: Int, target: Int): Int {
            var sample = 1
            while (min(width, height) / (sample * 2) >= target) sample *= 2
            return sample
        }
    }
}
