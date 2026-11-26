package com.oyenter.lo.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oyenter.lo.data.PlayerUiState
import com.oyenter.lo.data.Settings
import com.oyenter.lo.ui.components.Cover
import com.oyenter.lo.ui.components.LoChip
import com.oyenter.lo.ui.components.formatDuration
import com.oyenter.lo.ui.theme.Amber
import com.oyenter.lo.ui.theme.Bg
import com.oyenter.lo.ui.theme.Faint
import com.oyenter.lo.ui.theme.Ink
import com.oyenter.lo.ui.theme.Muted
import com.oyenter.lo.ui.theme.Surface1

@Composable
fun PlayerScreen(
    vm: AppVm,
    settings: Settings,
    state: PlayerUiState,
    accent: Color,
    hasNext: Boolean,
    onClose: () -> Unit,
    onTuning: () -> Unit,
    onEqualizer: () -> Unit,
    onVolume: () -> Unit,
    onQueue: () -> Unit,
    onSleep: () -> Unit,
    onMore: () -> Unit,
) {
    val current = state.current ?: return
    val track = current.fakeTrack
    val allTracks by vm.tracks.collectAsStateWithLifecycle()
    val sleepAt by vm.sleepEndsAt.collectAsStateWithLifecycle()
    val isFavorite = allTracks.firstOrNull { it.id == current.id }?.favorite == true

    var drag by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1)
    val shown = (dragging ?: state.positionMs.toFloat()).coerceIn(0f, duration.toFloat())

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to accent.copy(alpha = 0.55f),
                    0.42f to Bg,
                    1f to Bg,
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = {
                            if (drag > 160f) onClose()
                            drag = 0f
                        },
                        onVerticalDrag = { _, delta -> drag = (drag + delta).coerceAtLeast(0f) },
                    )
                },
        ) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .width(38.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Свернуть", tint = Ink, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "СЕЙЧАС ИГРАЕТ",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onMore) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Ещё", tint = Ink)
                }
            }

            Spacer(Modifier.weight(0.5f))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f)
                    .align(Alignment.CenterHorizontally)
                    .pointerInput(state.positionMs) {
                        detectTapGestures(onDoubleTap = { offset ->
                            val delta = if (offset.x < size.width / 2f) -10_000L else 10_000L
                            vm.seekTo((state.positionMs + delta).coerceAtLeast(0))
                        })
                    },
            ) {
                Surface(
                    color = Surface1,
                    shape = RoundedCornerShape(26.dp),
                    shadowElevation = 18.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Cover(track, size = null, radius = 26.dp, modifier = Modifier.fillMaxSize())
                }
            }

            Spacer(Modifier.weight(0.4f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        current.title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        listOf(current.artist, current.album).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { vm.toggleFavorite(track) }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "В избранное",
                        tint = if (isFavorite) Amber else Muted,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Slider(
                value = shown,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { vm.seekTo(it.toLong()) }
                    dragging = null
                },
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Amber,
                    activeTrackColor = Amber,
                    inactiveTrackColor = Color.White.copy(alpha = 0.12f),
                ),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    formatDuration(shown.toLong()),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = Faint,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "-${formatDuration((duration - shown.toLong()).coerceAtLeast(0))}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = Faint,
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = { vm.toggleShuffleForUi() }) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Перемешать",
                        tint = if (state.shuffle) Amber else Muted,
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(onClick = { vm.previous() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Назад", tint = Ink, modifier = Modifier.size(34.dp))
                }
                Surface(
                    color = Amber,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(76.dp)
                        .clickable { vm.toggle() },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "Пауза" else "Играть",
                            tint = Bg,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                IconButton(onClick = { vm.next() }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Вперёд",
                        tint = if (hasNext) Ink else Faint,
                        modifier = Modifier.size(34.dp),
                    )
                }
                IconButton(onClick = { vm.cycleRepeatForUi() }) {
                    Icon(
                        if (state.repeat == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Повтор",
                        tint = if (state.repeat != 0) Amber else Muted,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoChip(
                    text = "%.2f×".format(settings.speed),
                    selected = settings.speed != 1f,
                    onClick = onTuning,
                    leading = { Icon(Icons.Rounded.Speed, null, tint = if (settings.speed != 1f) Amber else Muted, modifier = Modifier.size(14.dp)) },
                )
                LoChip(
                    text = if (settings.pitchSemi == 0) "Тон" else "Тон ${if (settings.pitchSemi > 0) "+" else ""}${settings.pitchSemi}",
                    selected = settings.pitchSemi != 0,
                    onClick = onTuning,
                )
                LoChip(
                    text = if (settings.extraVolume == 1f) "Громкость" else "${(settings.extraVolume * 100).toInt()}%",
                    selected = settings.extraVolume != 1f,
                    onClick = onVolume,
                    leading = { Icon(Icons.Rounded.VolumeUp, null, tint = if (settings.extraVolume != 1f) Amber else Muted, modifier = Modifier.size(14.dp)) },
                )
                LoChip(
                    text = "EQ",
                    selected = settings.eqEnabled,
                    onClick = onEqualizer,
                    leading = { Icon(Icons.Rounded.GraphicEq, null, tint = if (settings.eqEnabled) Amber else Muted, modifier = Modifier.size(14.dp)) },
                )
                LoChip(
                    text = "Очередь",
                    selected = false,
                    onClick = onQueue,
                    leading = { Icon(Icons.Rounded.QueueMusic, null, tint = Muted, modifier = Modifier.size(14.dp)) },
                )
                LoChip(
                    text = "Таймер",
                    selected = sleepAt > 0,
                    onClick = onSleep,
                    leading = { Icon(Icons.Rounded.Timer, null, tint = if (sleepAt > 0) Amber else Muted, modifier = Modifier.size(14.dp)) },
                )
            }

            Spacer(Modifier.height(18.dp))
        }
    }
}
