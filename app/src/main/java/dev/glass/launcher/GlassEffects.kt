package dev.glass.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View

/**
 * GPU-only glass effects. Nothing in this file allocates a bitmap.
 *
 * Android's RenderEffect can only sample the view's *own* display list, there is
 * no public backdrop-blur API for a view, so the "frosted wallpaper" comes from
 * `Window.setBackgroundBlurRadius` (the system blurs the wallpaper behind our
 * transparent window, API 31+), while:
 *
 *  - [attachPanel] runs an AGSL runtime shader on the glass panels themselves:
 *    it bends the panel's own pixels near the rounded rim (refraction) and adds a
 *    specular top-left catch light. API 33+ (`RuntimeShader`).
 *  - [GlassShadow] is a solid rounded rect blurred with
 *    `RenderEffect.createBlurEffect` on the GPU, giving the dock a real,
 *    resolution-independent ambient occlusion pass with no bitmap involved.
 *
 * Both are skipped entirely below API 31; the procedural [LiquidGlassDrawable]
 * carries the look on its own there.
 */
object GlassEffects {

    /**
     * AGSL. `content` is the panel's rendered display list supplied by
     * RenderEffect.createRuntimeShaderEffect(view, "content").
     *
     * sd      : signed distance to the rounded rect (negative inside)
     * rim     : 1 at the very edge, fading out ~26px inwards
     * refract : pushes the sample along the edge normal, so the icons and text
     *           inside the panel appear to bend through thick glass at the rim
     */
    private const val AGSL = """
uniform shader content;
uniform float2 size;
uniform float corner;
uniform float refract;
uniform float gloss;

half4 main(float2 p) {
    float2 c = size * 0.5;
    float2 q = abs(p - c) - (c - float2(corner));
    float sd = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - corner;
    float inside = -sd;
    float rim = 1.0 - smoothstep(0.0, 26.0, inside);
    float2 dir = p - c;
    float2 n = dir / max(length(dir), 0.001);
    half4 src = content.eval(p + n * (refract * rim));
    float spec = pow(max(dot(n, float2(-0.7071, -0.7071)), 0.0), 3.0) * rim * gloss;
    return half4(src.rgb + half3(spec), src.a);
}
"""

    /** Re-applies the shader whenever the panel is measured or resized. */
    fun attachPanel(view: View, prefs: GlassPrefs) {
        if (Build.VERSION.SDK_INT < 33) return
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> applyPanel(v, prefs) }
        view.post { applyPanel(view, prefs) }
    }

    private fun applyPanel(view: View, prefs: GlassPrefs) {
        if (Build.VERSION.SDK_INT < 33) return
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) return
        val density = view.resources.displayMetrics.density
        try {
            val shader = RuntimeShader(AGSL)
            shader.setFloatUniform("size", width.toFloat(), height.toFloat())
            shader.setFloatUniform("corner", prefs.cornerRadius * density)
            shader.setFloatUniform("refract", 6f * density * (0.5f + prefs.blurRadius / 40f))
            shader.setFloatUniform("gloss", 0.20f + prefs.tintPercent / 150f)
            view.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
        } catch (t: Throwable) {
            // A driver that rejects AGSL simply gets the procedural fallback.
            view.setRenderEffect(null)
        }
    }
}

/**
 * Solid rounded rectangle + hardware blur = a genuine soft shadow on the GPU.
 * The shape is inset inside the view so the blurred falloff has room to bleed.
 */
class GlassShadow(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shape = RectF()
    private val density = context.resources.displayMetrics.density

    var sideInsetDp: Float = 22f
    var bottomInsetDp: Float = 8f
    var shapeHeightDp: Float = 84f
    var cornerDp: Float = 42f
    var strength: Int = 130

    private var blurDp: Float = 16f

    init {
        applyBlur()
    }

    fun setBlurDp(value: Float) {
        if (blurDp == value) return
        blurDp = value
        applyBlur()
    }

    /** Real GPU blur: no bitmap, no CPU pass, clipped to this view's bounds. */
    fun applyBlur() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                val radius = (blurDp * density).coerceAtLeast(0.1f)
                setRenderEffect(
                    RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                )
            } catch (t: Throwable) {
                setRenderEffect(null)
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val bottom = height - bottomInsetDp * density
        val top = bottom - shapeHeightDp * density
        shape.set(
            sideInsetDp * density,
            top,
            width - sideInsetDp * density,
            bottom
        )
        paint.color = Color.argb(strength, 0, 0, 0)
        canvas.drawRoundRect(shape, cornerDp * density, cornerDp * density, paint)
    }
}
