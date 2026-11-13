package app.liquidglass.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * [sharp] is the screen-sized wallpaper; [blurred] is a small heavily blurred copy of it that
 * every glass panel samples. [scale] is the downsample factor between the two.
 */
data class Backdrop(
    val sharp: ImageBitmap,
    val blurred: ImageBitmap,
    val scale: Int
)

object Imaging {

    private const val BLUR_SCALE = 8

    fun buildBackdrop(context: Context, width: Int, height: Int): Backdrop {
        val screen = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(screen)

        val wallpaper = runCatching { WallpaperManager.getInstance(context).drawable }.getOrNull()
        if (wallpaper != null && wallpaper.intrinsicWidth > 0 && wallpaper.intrinsicHeight > 0) {
            val iw = wallpaper.intrinsicWidth.toFloat()
            val ih = wallpaper.intrinsicHeight.toFloat()
            val factor = max(width / iw, height / ih)
            val dw = (iw * factor).roundToInt().coerceAtLeast(1)
            val dh = (ih * factor).roundToInt().coerceAtLeast(1)
            val left = (width - dw) / 2
            val top = (height - dh) / 2
            wallpaper.setBounds(left, top, left + dw, top + dh)
            wallpaper.draw(canvas)
        } else {
            drawFallbackWallpaper(canvas, width, height)
        }

        // Slight darkening keeps white glass text readable on bright wallpapers.
        canvas.drawColor(android.graphics.Color.argb(40, 0, 0, 0))

        val blurred = blur(screen, BLUR_SCALE, radius = 2, passes = 2)
        return Backdrop(screen.asImageBitmap(), blurred.asImageBitmap(), BLUR_SCALE)
    }

    fun drawableToImageBitmap(drawable: Drawable, sizePx: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }

    private fun blur(src: Bitmap, scale: Int, radius: Int, passes: Int): Bitmap {
        val sw = max(1, src.width / scale)
        val sh = max(1, src.height / scale)
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val pixels = IntArray(sw * sh)
        small.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        repeat(passes) { boxBlur(pixels, sw, sh, radius) }
        val out = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, sw, 0, 0, sw, sh)
        return out
    }

    /** Separable box blur, sliding window, in place. Cheap enough to run once on the UI thread budget. */
    private fun boxBlur(pixels: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(pixels.size)
        val divisor = 2 * r + 1

        for (y in 0 until h) {
            val row = y * w
            var a = 0
            var red = 0
            var green = 0
            var blue = 0
            for (i in -r..r) {
                val p = pixels[row + i.coerceIn(0, w - 1)]
                a += (p ushr 24) and 0xFF
                red += (p shr 16) and 0xFF
                green += (p shr 8) and 0xFF
                blue += p and 0xFF
            }
            for (x in 0 until w) {
                tmp[row + x] = (((a / divisor).coerceIn(0, 255)) shl 24) or
                    (((red / divisor).coerceIn(0, 255)) shl 16) or
                    (((green / divisor).coerceIn(0, 255)) shl 8) or
                    ((blue / divisor).coerceIn(0, 255))
                val out = pixels[row + (x - r).coerceIn(0, w - 1)]
                val incoming = pixels[row + (x + r + 1).coerceIn(0, w - 1)]
                a += ((incoming ushr 24) and 0xFF) - ((out ushr 24) and 0xFF)
                red += ((incoming shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                green += ((incoming shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                blue += (incoming and 0xFF) - (out and 0xFF)
            }
        }

        for (x in 0 until w) {
            var a = 0
            var red = 0
            var green = 0
            var blue = 0
            for (i in -r..r) {
                val p = tmp[i.coerceIn(0, h - 1) * w + x]
                a += (p ushr 24) and 0xFF
                red += (p shr 16) and 0xFF
                green += (p shr 8) and 0xFF
                blue += p and 0xFF
            }
            for (y in 0 until h) {
                pixels[y * w + x] = (((a / divisor).coerceIn(0, 255)) shl 24) or
                    (((red / divisor).coerceIn(0, 255)) shl 16) or
                    (((green / divisor).coerceIn(0, 255)) shl 8) or
                    ((blue / divisor).coerceIn(0, 255))
                val out = tmp[(y - r).coerceIn(0, h - 1) * w + x]
                val incoming = tmp[(y + r + 1).coerceIn(0, h - 1) * w + x]
                a += ((incoming ushr 24) and 0xFF) - ((out ushr 24) and 0xFF)
                red += ((incoming shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                green += ((incoming shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                blue += (incoming and 0xFF) - (out and 0xFF)
            }
        }
    }

    private fun drawFallbackWallpaper(canvas: Canvas, w: Int, h: Int) {
        val width = w.toFloat()
        val height = h.toFloat()
        canvas.drawColor(0xFF0A0A16.toInt())

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            width * 0.18f, height * 0.10f, height * 0.62f,
            intArrayOf(0xFF3A2A8C.toInt(), 0x003A2A8C),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width, height, paint)

        paint.shader = RadialGradient(
            width * 0.95f, height * 0.28f, height * 0.52f,
            intArrayOf(0xFF1C5E90.toInt(), 0x001C5E90),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width, height, paint)

        paint.shader = RadialGradient(
            width * 0.55f, height * 0.96f, height * 0.55f,
            intArrayOf(0xFF8A2B63.toInt(), 0x008A2B63),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width, height, paint)
    }
}
