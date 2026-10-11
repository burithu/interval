package io.github.burithu.interval.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.burithu.interval.ui.theme.IntervalTheme
import io.github.burithu.interval.ui.theme.Sizes
import io.github.burithu.interval.ui.theme.Spacing
import io.github.burithu.interval.ui.theme.Strokes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Line icons drawn on a 24-unit grid with the rule stroke, so the app needs no icon library and
 * no glyph depends on a fallback font.
 */
enum class Glyph { GEAR, BACK, CLOSE, ARROW_RIGHT }

@Composable
fun GlyphIcon(glyph: Glyph, modifier: Modifier = Modifier, color: Color = IntervalTheme.colors.fg) {
    Canvas(modifier.size(Sizes.icon)) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = Strokes.rule.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (glyph) {
            Glyph.GEAR -> {
                drawPath(gearPath(u), color, style = stroke)
                drawCircle(color, radius = 3.2f * u, center = Offset(12 * u, 12 * u), style = stroke)
            }
            Glyph.BACK -> {
                line(color, stroke, u, 20f, 12f, 4f, 12f)
                line(color, stroke, u, 10f, 6f, 4f, 12f)
                line(color, stroke, u, 10f, 18f, 4f, 12f)
            }
            Glyph.ARROW_RIGHT -> {
                line(color, stroke, u, 4f, 12f, 20f, 12f)
                line(color, stroke, u, 14f, 6f, 20f, 12f)
                line(color, stroke, u, 14f, 18f, 20f, 12f)
            }
            Glyph.CLOSE -> {
                line(color, stroke, u, 6f, 6f, 18f, 18f)
                line(color, stroke, u, 18f, 6f, 6f, 18f)
            }
        }
    }
}

/** A glyph centred in a 48dp touch target. */
@Composable
fun IconButton(glyph: Glyph, label: String, onClick: () -> Unit, color: Color = IntervalTheme.colors.fg) {
    Box(
        Modifier.size(Spacing.minTouch)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, color = color) }
}

private fun DrawScope.line(color: Color, stroke: Stroke, u: Float, x1: Float, y1: Float, x2: Float, y2: Float) =
    drawLine(color, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), strokeWidth = stroke.width, cap = stroke.cap)

/** Eight teeth: each tooth steps from the root circle out to the tip circle and back. */
private fun gearPath(u: Float): Path {
    val teeth = 8
    val root = 7.6f
    val tip = 10f
    val half = PI.toFloat() / teeth
    return Path().apply {
        var first = true
        for (i in 0 until teeth) {
            val a = i * 2 * half
            for ((r, da) in listOf(root to -half * .55f, tip to -half * .3f, tip to half * .3f, root to half * .55f)) {
                val x = (12 + r * cos(a + da)) * u
                val y = (12 + r * sin(a + da)) * u
                if (first) moveTo(x, y).also { first = false } else lineTo(x, y)
            }
        }
        close()
    }
}
