package com.oyenter.lo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oyenter.lo.playback.EqController
import com.oyenter.lo.ui.components.LoChip
import com.oyenter.lo.ui.theme.Amber
import com.oyenter.lo.ui.theme.Bg
import com.oyenter.lo.ui.theme.Faint
import com.oyenter.lo.ui.theme.Ink
import com.oyenter.lo.ui.theme.Muted
import com.oyenter.lo.ui.theme.Surface2
import kotlin.math.roundToInt

private data class Preset(val name: String, val gains: List<Float>, val preamp: Float = 0f)

private val presets = listOf(
    Preset("Ровно", List(10) { 0f }),
    Preset("Бас", listOf(7f, 6f, 4f, 2f, 0f, -1f, -1f, 0f, 1f, 1f), -2f),
    Preset("Тёплый", listOf(4f, 4f, 3f, 1f, 0f, -1f, 0f, 1f, 2f, 1f), -1f),
    Preset("Голос", listOf(-3f, -2f, 0f, 3f, 5f, 4f, 2f, 0f, -1f, -2f)),
    Preset("Рок", listOf(5f, 4f, 2f, -1f, -2f, 0f, 2f, 4f, 5f, 4f), -1f),
    Preset("Подкаст", listOf(-5f, -3f, 0f, 3f, 5f, 4f, 3f, 1f, -1f, -3f)),
)

@Composable
fun EqualizerSection(
    gains: List<Float>,
    preamp: Float,
    enabled: Boolean,
    onChange: (List<Float>, Float, Boolean) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("10 полос", style = MaterialTheme.typography.titleLarge, color = Ink)
                Text(
                    "Тяни точки. Двойное касание линии — ровно.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { onChange(gains, preamp, it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Bg,
                    checkedTrackColor = Amber,
                    uncheckedThumbColor = Muted,
                    uncheckedTrackColor = Surface2,
                ),
            )
        }

        Spacer(Modifier.height(10.dp))

        EqualizerCurve(
            gains = gains,
            enabled = enabled,
            onChange = { index, value ->
                val next = gains.toMutableList()
                next[index] = value
                onChange(next, preamp, true)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.03f)),
        )

        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
            val labels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Faint,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.take(3).forEach { preset ->
                LoChip(text = preset.name, selected = false, onClick = {
                    onChange(preset.gains, preset.preamp, true)
                })
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.drop(3).forEach { preset ->
                LoChip(text = preset.name, selected = false, onClick = {
                    onChange(preset.gains, preset.preamp, true)
                })
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Предусиление ${if (preamp >= 0) "+" else ""}${"%.1f".format(preamp)} дБ",
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
        )
        Slider(
            value = preamp,
            onValueChange = { onChange(gains, it, enabled) },
            valueRange = -12f..6f,
            colors = SliderDefaults.colors(
                thumbColor = Amber,
                activeTrackColor = Amber,
                inactiveTrackColor = Surface2,
            ),
        )
        Row {
            LoChip(text = "Сбросить", selected = false, onClick = {
                onChange(List(10) { 0f }, 0f, enabled)
            })
            Spacer(Modifier.width(8.dp))
            LoChip(text = "Максимум", selected = false, onClick = {
                onChange(listOf(12f, 12f, 12f, 12f, 12f, 12f, 12f, 12f, 12f, 12f), -6f, enabled)
            })
        }
    }
}

@Composable
private fun EqualizerCurve(
    gains: List<Float>,
    enabled: Boolean,
    onChange: (Int, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestGains = rememberUpdatedState(gains)
    val latestOnChange = rememberUpdatedState(onChange)
    val bands = EqController.FREQS.size

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var active = -1
                    fun gainAt(y: Float): Float {
                        val ratio = (y / size.height).coerceIn(0f, 1f)
                        return (12f - ratio * 24f).coerceIn(-12f, 12f)
                    }
                    detectDragGestures(
                        onDragStart = { offset ->
                            active = ((offset.x / size.width) * (bands - 1)).roundToInt().coerceIn(0, bands - 1)
                            latestOnChange.value(active, gainAt(offset.y))
                        },
                        onDragEnd = { active = -1 },
                        onDragCancel = { active = -1 },
                        onDrag = { change, _ ->
                            change.consume()
                            if (active < 0) {
                                active = ((change.position.x / size.width) * (bands - 1)).roundToInt().coerceIn(0, bands - 1)
                            }
                            latestOnChange.value(active, gainAt(change.position.y))
                        },
                    )
                },
        ) {
            val w = size.width
            val h = size.height
            val padX = 14.dp.toPx()
            val padY = 12.dp.toPx()
            val usableW = w - padX * 2
            val usableH = h - padY * 2
            val lineColor = Color.White.copy(alpha = 0.07f)

            for (db in listOf(12, 6, 0, -6, -12)) {
                val y = padY + (1f - (db + 12f) / 24f) * usableH
                drawLine(
                    color = if (db == 0) Color.White.copy(alpha = 0.14f) else lineColor,
                    start = Offset(padX, y),
                    end = Offset(w - padX, y),
                    strokeWidth = if (db == 0) 1.4.dp.toPx() else 1.dp.toPx(),
                )
            }
            for (i in 0 until bands) {
                val x = padX + usableW * i / (bands - 1f)
                drawLine(lineColor, Offset(x, padY), Offset(x, h - padY), 1.dp.toPx())
            }

            val points = (0 until bands).map { i ->
                val g = (latestGains.value.getOrNull(i) ?: 0f).coerceIn(-12f, 12f)
                Offset(
                    x = padX + usableW * i / (bands - 1f),
                    y = padY + (1f - (g + 12f) / 24f) * usableH,
                )
            }

            val fill = Path().apply {
                moveTo(points.first().x, h - padY)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, h - padY)
                close()
            }
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    listOf(Amber.copy(alpha = if (enabled) 0.28f else 0.08f), Color.Transparent),
                    startY = padY,
                    endY = h - padY,
                ),
            )

            val line = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path = line,
                color = if (enabled) Amber else Muted,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
            )

            points.forEach { point ->
                drawCircle(color = Bg, radius = 6.dp.toPx(), center = point)
                drawCircle(
                    color = if (enabled) Amber else Muted,
                    radius = 4.dp.toPx(),
                    center = point,
                )
            }
        }
    }
}
