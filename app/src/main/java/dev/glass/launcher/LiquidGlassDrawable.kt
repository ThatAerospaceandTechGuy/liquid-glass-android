package dev.glass.launcher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.max

/**
 * A completely procedural "liquid glass" surface.
 *
 * Layer order:
 *   1. three passes of rounded rect shadow (fake gaussian falloff, no filter)
 *   2. dark scrim so white type stays legible on a bright wallpaper
 *   3. frosted fill: diagonal white gradient at the tint alpha
 *   4. specular rim: 1dp diagonal gradient stroke, bright top-left, bright bottom-right
 *
 * Everything is four reused [Paint] objects and three shaders that are rebuilt
 * only when bounds or a setting actually change. No bitmaps, no XML, no filters,
 * so a whole 4x5 grid of these costs a few KB of heap.
 */
class LiquidGlassDrawable(
    private val density: Float,
    private val prefs: GlassPrefs? = null
) : Drawable() {

    var cornerRadiusDp: Float = 18f
        set(value) {
            if (field != value) {
                field = value
                radiusDirty = true
                invalidateSelf()
            }
        }

    var tintAlpha: Int = 70
        set(value) {
            if (field != value) {
                field = value
                shaderDirty = true
                invalidateSelf()
            }
        }

    var shadowDp: Float = 6f
        set(value) {
            if (field != value) {
                field = value
                shaderDirty = true
                invalidateSelf()
            }
        }

    /** When true the shape is a stadium (the dock pill and the search field). */
    var pill: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                radiusDirty = true
                invalidateSelf()
            }
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val flatPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val boundsF = RectF()
    private val content = RectF()
    private var radius = 0f
    private var radiusDirty = true
    private var shaderDirty = true
    private var fillShader: Shader? = null
    private var rimShader: Shader? = null
    private var drawAlpha = 255

    /**
     * Copies the live settings into this drawable. Called from onDraw, so a
     * slider drag restyles on the next frame without touching the view tree.
     */
    fun sync() {
        val p = prefs ?: return
        val radiusDp = p.cornerRadius.toFloat()
        val alpha = 24 + p.tintPercent * 3
        if (cornerRadiusDp != radiusDp) {
            cornerRadiusDp = radiusDp
            radiusDirty = true
        }
        if (tintAlpha != alpha) {
            tintAlpha = alpha
            shaderDirty = true
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        boundsF.set(bounds)
        radiusDirty = true
        shaderDirty = true
    }

    override fun draw(canvas: Canvas) {
        if (boundsF.width() <= 1f || boundsF.height() <= 1f) return

        val shadow = shadowDp * density
        content.set(
            boundsF.left + shadow,
            boundsF.top + shadow,
            boundsF.right - shadow,
            boundsF.bottom - shadow
        )
        if (content.width() <= 1f || content.height() <= 1f) content.set(boundsF)

        if (radiusDirty) {
            val raw = if (pill) content.height() * 0.5f else cornerRadiusDp * density
            radius = raw.coerceAtMost(content.height() * 0.5f).coerceAtMost(content.width() * 0.5f)
            radiusDirty = false
        }
        if (shaderDirty || fillShader == null) buildShaders()

        // 1. fake gaussian shadow: three growing rects, each a little fainter
        if (shadow > 0.5f) {
            var grow = 0f
            for (i in 0 until 3) {
                flatPaint.color = Color.argb(shadowAlpha() / (i + 2), 0, 0, 0)
                canvas.drawRoundRect(
                    content.left - grow,
                    content.top + shadow * 0.25f - grow * 0.5f,
                    content.right + grow,
                    content.bottom + shadow * 0.55f + grow,
                    radius + grow,
                    radius + grow,
                    flatPaint
                )
                grow += shadow / 3f
            }
        }

        // 2. dark scrim under the frost
        flatPaint.color = Color.argb((26 * drawAlpha) / 255, 8, 10, 14)
        canvas.drawRoundRect(content, radius, radius, flatPaint)

        // 3. frosted fill
        fillPaint.shader = fillShader
        fillPaint.alpha = 255
        canvas.drawRoundRect(content, radius, radius, fillPaint)

        // 4. specular rim: 1dp diagonal gradient stroke
        rimPaint.strokeWidth = max(1f, density * 0.9f)
        rimPaint.shader = rimShader
        val half = rimPaint.strokeWidth * 0.5f
        canvas.drawRoundRect(
            content.left + half,
            content.top + half,
            content.right - half,
            content.bottom - half,
            radius,
            radius,
            rimPaint
        )

        // 5. extra glint across the top-left third, the "liquid" catch light
        sheenPaint.shader = rimShader
        sheenPaint.alpha = (110 * drawAlpha) / 255
        sheenPaint.strokeWidth = max(1f, density * 2.2f)
        sheenPaint.style = Paint.Style.STROKE
        canvas.drawArc(
            content.left + half,
            content.top + half,
            content.right - half,
            content.bottom - half,
            196f,
            46f,
            false,
            sheenPaint
        )
    }

    private fun shadowAlpha(): Int = (60 * drawAlpha) / 255

    private fun buildShaders() {
        val alpha = ((tintAlpha * drawAlpha) / 255).coerceIn(0, 255)
        val white = Color.WHITE
        fillShader = LinearGradient(
            content.left,
            content.top,
            content.right,
            content.bottom,
            intArrayOf(
                Color.argb(alpha, 255, 255, 255),
                Color.argb((alpha * 35) / 100, 255, 255, 255),
                Color.argb((alpha * 12) / 100, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        rimShader = LinearGradient(
            content.left,
            content.top,
            content.right,
            content.bottom,
            intArrayOf(
                Color.argb((210 * drawAlpha) / 255, Color.red(white), Color.green(white), Color.blue(white)),
                Color.argb((36 * drawAlpha) / 255, 255, 255, 255),
                Color.argb((120 * drawAlpha) / 255, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        shaderDirty = false
    }

    override fun setAlpha(alpha: Int) {
        val clamped = alpha.coerceIn(0, 255)
        if (drawAlpha != clamped) {
            drawAlpha = clamped
            shaderDirty = true
            invalidateSelf()
        }
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        rimPaint.colorFilter = colorFilter
        sheenPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = -1

    override fun getIntrinsicHeight(): Int = -1
}
