package com.oyenter.lo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oyenter.lo.data.Playlist
import com.oyenter.lo.data.Settings
import com.oyenter.lo.data.SortMode
import com.oyenter.lo.data.Track
import com.oyenter.lo.ui.components.Cover
import com.oyenter.lo.ui.components.LoChip
import com.oyenter.lo.ui.components.formatDuration
import com.oyenter.lo.ui.theme.Amber
import com.oyenter.lo.ui.theme.Bg
import com.oyenter.lo.ui.theme.Faint
import com.oyenter.lo.ui.theme.Ink
import com.oyenter.lo.ui.theme.Muted
import com.oyenter.lo.ui.theme.Surface1
import com.oyenter.lo.ui.theme.Surface2
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHost(
    sheet: Sheet?,
    vm: AppVm,
    settings: Settings,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSheet: (Sheet) -> Unit,
    onDetail: (Detail) -> Unit,
) {
    if (sheet == null) return
    val sleepAt by vm.sleepEndsAt.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        contentColor = Ink,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)),
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp)) {
            when (sheet) {
                Sheet.Sort -> SortSheet(vm)
                Sheet.Tuning -> TuningSheet(vm, settings)
                Sheet.Equalizer -> EqualizerSection(
                    gains = settings.eqGains,
                    preamp = settings.preamp,
                    enabled = settings.eqEnabled,
                    onChange = vm::setEq,
                )

                Sheet.Queue -> QueueSheet(vm)
                Sheet.Sleep -> SleepSheet(sleepAt > 0, vm::setSleep)
                is Sheet.TrackMenu -> TrackMenuSheet(
                    vm = vm,
                    track = sheet.track,
                    onSheet = onSheet,
                    onDetail = onDetail,
                    onDismiss = onDismiss,
                )

                is Sheet.AddToPlaylist -> AddToPlaylistSheet(
                    vm = vm,
                    playlists = playlists,
                    trackIds = sheet.trackIds,
                    onDismiss = onDismiss,
                )

                Sheet.CreatePlaylist -> CreatePlaylistSheet(vm, onDismiss)
                Sheet.Folders -> FoldersSheet(vm, onDismiss)
            }
        }
    }
}

@Composable
private fun SortSheet(vm: AppVm) {
    val filters by vm.filters.collectAsStateWithLifecycle()
    Text("Сортировка", style = MaterialTheme.typography.headlineSmall, color = Ink)
    Spacer(Modifier.height(8.dp))
    SortMode.entries.forEach { mode ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { vm.setSort(mode) }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(mode.label, style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f))
            if (filters.sort == mode) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp))
            }
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { vm.toggleAscending() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (filters.asc) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
            contentDescription = null,
            tint = Amber,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            if (filters.asc) "По возрастанию" else "По убыванию",
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
        )
    }
}

@Composable
private fun TuningSheet(vm: AppVm, settings: Settings) {
    var speed by remember { mutableFloatStateOf(settings.speed) }
    var pitch by remember { mutableIntStateOf(settings.pitchSemi) }

    Text("Скорость", style = MaterialTheme.typography.headlineSmall, color = Ink)
    Text(
        "%.2f×".format(speed),
        style = MaterialTheme.typography.displayMedium,
        color = Amber,
        fontFamily = FontFamily.Monospace,
    )
    Slider(
        value = speed,
        onValueChange = {
            speed = it
            vm.previewSpeed(it)
        },
        onValueChangeFinished = { vm.setSpeed(speed) },
        valueRange = 0.5f..3f,
        steps = 49,
        colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber, inactiveTrackColor = Surface2),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { preset ->
            LoChip(
                text = if (preset == 1f) "1×" else "%.2f×".format(preset),
                selected = speed == preset,
                onClick = {
                    speed = preset
                    vm.setSpeed(preset)
                },
            )
        }
    }

    Spacer(Modifier.height(18.dp))
    Text("Тон", style = MaterialTheme.typography.headlineSmall, color = Ink)
    Text(
        text = when {
            pitch == 0 -> "как в записи"
            pitch > 0 -> "+$pitch полутонов"
            else -> "$pitch полутонов"
        },
        style = MaterialTheme.typography.displayMedium,
        color = Amber,
        fontFamily = FontFamily.Monospace,
    )
    Slider(
        value = pitch.toFloat(),
        onValueChange = {
            pitch = it.roundToInt()
            vm.previewPitchSemi(pitch)
        },
        onValueChangeFinished = { vm.setPitchSemi(pitch) },
        valueRange = -12f..12f,
        steps = 23,
        colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber, inactiveTrackColor = Surface2),
    )

    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Pitch Lock", style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(
                "Ускоряй, не меняя высоту голоса",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
        Switch(
            checked = settings.pitchLock,
            onCheckedChange = vm::setPitchLock,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Bg,
                checkedTrackColor = Amber,
                uncheckedThumbColor = Muted,
                uncheckedTrackColor = Surface2,
            ),
        )
    }
    if (!settings.pitchLock) {
        Text(
            "Без блокировки скорость «тянет» тон — эффект плёнки.",
            style = MaterialTheme.typography.bodySmall,
            color = Faint,
        )
    }
    Spacer(Modifier.height(10.dp))
    LoChip(
        text = "Сбросить в 1× и 0",
        selected = false,
        onClick = {
            speed = 1f
            pitch = 0
            vm.setSpeed(1f)
            vm.setPitchSemi(0)
        },
    )
}

@Composable
private fun QueueSheet(vm: AppVm) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    Text("Очередь · ${queue.size}", style = MaterialTheme.typography.headlineSmall, color = Ink)
    Spacer(Modifier.height(6.dp))
    if (queue.isEmpty()) {
        Text("Пусто. Запусти трек из библиотеки.", style = MaterialTheme.typography.bodySmall, color = Muted)
        return
    }
    LazyColumn(Modifier.heightIn(max = 460.dp)) {
        items(queue.size) { index ->
            val item = queue[index]
            val current = item.id == player.current?.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.playQueueIndex(index) }
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "%02d".format(index + 1),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (current) Amber else Faint,
                    modifier = Modifier.width(30.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (current) Amber else Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${item.artist} · ${formatDuration(item.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                androidx.compose.material3.IconButton(onClick = { vm.removeFromQueue(index) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Убрать", tint = Faint, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SleepSheet(active: Boolean, onPick: (Int) -> Unit) {
    var chosen by remember { mutableStateOf(0) }
    Text("Таймер сна", style = MaterialTheme.typography.headlineSmall, color = Ink)
    Text(
        if (active) "Таймер уже идёт. Можно перезапустить или выключить." else "Музыка остановится сама.",
        style = MaterialTheme.typography.bodySmall,
        color = Muted,
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(15, 30, 45, 60, 90).forEach { minutes ->
            LoChip(
                text = "$minutes мин",
                selected = chosen == minutes,
                onClick = { chosen = minutes },
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    Row {
        TextButton(onClick = { if (chosen > 0) onPick(chosen) }) {
            Text(if (chosen > 0) "Запустить" else "Выбери время", color = if (chosen > 0) Amber else Faint)
        }
        TextButton(onClick = { onPick(0) }) { Text("Выключить", color = Muted) }
    }
}

@Composable
private fun TrackMenuSheet(
    vm: AppVm,
    track: Track,
    onSheet: (Sheet) -> Unit,
    onDetail: (Detail) -> Unit,
    onDismiss: () -> Unit,
) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Cover(track, 52.dp, 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${track.artist} · ${track.album.ifBlank { "без альбома" }}",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    MenuRow(Icons.Rounded.PlayArrow, "Играть") {
        val list = tracks
        val index = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        vm.play(list, index)
        onDismiss()
    }
    MenuRow(Icons.Rounded.QueueMusic, "Играть следующим") {
        vm.playNext(track)
        onDismiss()
    }
    MenuRow(Icons.Rounded.PlaylistPlay, "Добавить в очередь") {
        vm.enqueue(track)
        onDismiss()
    }
    MenuRow(
        if (track.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        if (track.favorite) "Убрать из избранного" else "В избранное",
    ) {
        vm.toggleFavorite(track)
        onDismiss()
    }
    MenuRow(Icons.Rounded.PlaylistAdd, "В плейлист") {
        onSheet(Sheet.AddToPlaylist(listOf(track.id)))
    }
    MenuRow(Icons.Rounded.Person, "Все треки артиста") {
        onDetail(Detail.Artist(track.artist))
        onDismiss()
    }
    if (track.album.isNotBlank()) {
        MenuRow(Icons.Rounded.Album, "Альбом целиком") {
            onDetail(Detail.Album(track.album, track.artist))
            onDismiss()
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Amber, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}

@Composable
private fun AddToPlaylistSheet(vm: AppVm, playlists: List<Playlist>, trackIds: List<Long>, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    Text("В плейлист", style = MaterialTheme.typography.headlineSmall, color = Ink)
    Spacer(Modifier.height(8.dp))
    playlists.forEach { playlist ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    vm.addToPlaylist(playlist.id, trackIds)
                    onDismiss()
                }
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PlaylistPlay, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(playlist.name, style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${playlist.trackCount}", style = MaterialTheme.typography.bodySmall, color = Faint)
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        singleLine = true,
        label = { Text("Новый плейлист") },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(
            onClick = {
                if (name.isNotBlank()) {
                    vm.addToPlaylistByName(name, trackIds)
                    onDismiss()
                }
            },
        ) { Text("Создать и добавить", color = Amber) }
    }
}

@Composable
private fun CreatePlaylistSheet(vm: AppVm, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    Text("Новый плейлист", style = MaterialTheme.typography.headlineSmall, color = Ink)
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        singleLine = true,
        label = { Text("Название") },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { onDismiss() }) { Text("Отмена", color = Muted) }
        TextButton(
            onClick = {
                if (name.isNotBlank()) {
                    vm.createPlaylist(name)
                    onDismiss()
                }
            },
        ) { Text("Создать", color = Amber) }
    }
}

@Composable
private fun FoldersSheet(vm: AppVm, onDismiss: () -> Unit) {
    val filters by vm.filters.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val folders = remember(tracks) {
        tracks.groupBy { it.folder }.filterKeys { it.isNotBlank() }.mapValues { it.value.size }.toList()
            .sortedByDescending { it.second }
    }
    Text("Папки", style = MaterialTheme.typography.headlineSmall, color = Ink)
    Spacer(Modifier.height(6.dp))
    LazyColumn(Modifier.heightIn(max = 420.dp)) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        vm.setFolder(null)
                        onDismiss()
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Все треки", style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f))
                if (filters.folder == null) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Amber, modifier = Modifier.size(16.dp))
                }
            }
        }
        items(folders, key = { it.first }) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        vm.setFolder(entry.first)
                        onDismiss()
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null, tint = if (filters.folder == entry.first) Amber else Muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(entry.first, style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${entry.second}", style = MaterialTheme.typography.bodySmall, color = Faint)
                if (filters.folder == entry.first) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Amber, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
