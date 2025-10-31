package tw.com.johnnyhng.eztalk.asr.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun WaveformDisplay(
    samples: FloatArray,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    scale: Float = 5.0f
) {
    Canvas(modifier = modifier) {
        drawWaveform(samples, color, scale)
    }
}

private fun DrawScope.drawWaveform(samples: FloatArray, color: Color, scale: Float) {
    if (samples.size < 2) return

    val path = Path()
    val width = size.width
    val height = size.height
    val centerY = height / 2

    // Simple downsampling if there are too many points to draw reasonably
    val step = (samples.size / (width / 4f)).toInt().coerceAtLeast(1)
    val drawableSamples = if (step > 1) samples.filterIndexed { index, _ -> index % step == 0 } else samples.toList()
    if (drawableSamples.size < 2) return

    val points = drawableSamples.mapIndexed { index, sample ->
        val x = index.toFloat() * width / (drawableSamples.size - 1)
        val y = centerY - (sample * centerY * scale).coerceIn(-centerY, centerY)
        Offset(x, y)
    }

    path.moveTo(points.first().x, points.first().y)

    for (i in 1 until points.size) {
        val midPoint = Offset((points[i].x + points[i - 1].x) / 2, (points[i].y + points[i - 1].y) / 2)
        path.quadraticBezierTo(
            points[i - 1].x,
            points[i - 1].y,
            midPoint.x,
            midPoint.y
        )
    }
    // Draw the last segment
    path.lineTo(points.last().x, points.last().y)


    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 3f, cap = StrokeCap.Round)
    )
}
