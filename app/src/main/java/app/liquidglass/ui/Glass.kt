package app.liquidglass.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.liquidglass.util.Backdrop
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/**
 * iOS-style continuous-curvature icon shape. A superellipse reads as a squircle at icon sizes.
 */
class SquircleShape(private val exponent: Float = 4.2f) : Shape {

    private val cache = HashMap<Long, Path>()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) return Outline.Generic(Path())

        val key = (size.width.toLong() shl 32) or (size.height.toLong() and 0xFFFFFFFFL)
        val path = cache.getOrPut(key) { build(size) }
        if (cache.size > 24) cache.clear()
        return Outline.Generic(path)
    }

    private fun build(size: Size): Path {
        val path = Path()
        val a = size.width / 2f
        val b = size.height / 2f
        val power = 2f / exponent
        val steps = 120
        for (i in 0..steps) {
            val t = i.toDouble() / steps * 2.0 * Math.PI
            val ct = cos(t).toFloat()
            val st = sin(t).toFloat()
            val x = a * sign(ct) * abs(ct).pow(power) + a
            val y = b * sign(st) * abs(st).pow(power) + b
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }
}

/** Tap target without a ripple, so touches stay glassy instead of Material. */
@Composable
fun Modifier.glassClick(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return clickable(interactionSource = source, indication = null, onClick = onClick)
}

/**
 * A rounded glass panel. The frosted backdrop is the wallpaper blurred once at startup and
 * sampled at this panel's position, so every panel refracts what sits behind it for free.
 */
@Composable
fun GlassBox(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    tint: Color = Color(0x16FFFFFF),
    gloss: Float = 1f,
    elevation: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit
) {
    var position by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .onGloballyPositioned { position = it.positionInRoot() }
            .shadow(elevation = elevation, shape = shape, clip = false)
            .clip(shape)
            .drawBehind {
                drawBackdropCrop(backdrop, position)
                drawRect(tint)
                if (gloss > 0f) {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.20f * gloss),
                            0.42f to Color.White.copy(alpha = 0.05f * gloss),
                            1f to Color.White.copy(alpha = 0.03f * gloss)
                        )
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f * gloss),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.22f, -size.height * 0.3f),
                            radius = size.maxDimension * 0.9f
                        )
                    )
                }
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.62f),
                            Color.White.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.28f)
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    ),
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                    style = Stroke(width = stroke)
                )
            },
        content = content
    )
}

private fun DrawScope.drawBackdropCrop(backdrop: Backdrop?, position: Offset) {
    if (backdrop == null) return
    val bitmap = backdrop.blurred
    val factor = backdrop.scale.toFloat()
    val bw = bitmap.width
    val bh = bitmap.height
    if (bw <= 0 || bh <= 0) return

    val sx = (position.x / factor).roundToInt().coerceIn(0, maxOf(0, bw - 1))
    val sy = (position.y / factor).roundToInt().coerceIn(0, maxOf(0, bh - 1))
    val sw = (size.width / factor).roundToInt().coerceIn(1, maxOf(1, bw - sx))
    val sh = (size.height / factor).roundToInt().coerceIn(1, maxOf(1, bh - sy))

    drawImage(
        image = bitmap,
        srcOffset = IntOffset(sx, sy),
        srcSize = IntSize(sw, sh),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(
            size.width.roundToInt().coerceAtLeast(1),
            size.height.roundToInt().coerceAtLeast(1)
        ),
        filterQuality = FilterQuality.Medium
    )
}
