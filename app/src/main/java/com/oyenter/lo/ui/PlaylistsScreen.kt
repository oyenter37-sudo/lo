package com.oyenter.lo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oyenter.lo.data.Playlist
import com.oyenter.lo.data.SmartList
import com.oyenter.lo.data.Track
import com.oyenter.lo.ui.components.Cover
import com.oyenter.lo.ui.components.LoChip
import com.oyenter.lo.ui.components.SectionLabel
import com.oyenter.lo.ui.components.TrackRow
import com.oyenter.lo.ui.theme.Amber
import com.oyenter.lo.ui.theme.Faint
import com.oyenter.lo.ui.theme.Ink
import com.oyenter.lo.ui.theme.Muted
import com.oyenter.lo.ui.theme.Surface1
import com.oyenter.lo.ui.theme.Surface2

@Composable
fun PlaylistsScreen(
    vm: AppVm,
    onOpen: (Long, String) -> Unit,
    onOpenSmart: (String, String) -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit,
) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 10.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Плейлисты", style = MaterialTheme.typography.displayMedium, color = Ink)
                    Text(
                        "${playlists.size} своих · ${tracks.size} треков",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
                IconButton(onClick = onImport) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = "Импорт M3U8", tint = Muted)
                }
                IconButton(onClick = onCreate) {
                    Icon(Icons.Rounded.Add, contentDescription = "Создать", tint = Ink)
                }
            }
        }

        item { SectionLabel("Умные подборки") }
        items(SmartList.entries, key = { it.key }) { smart ->
            val count = vm.smartTracks(smart).size
            SmartRow(
                title = smart.title,
                subtitle = "${smart.subtitle} · $count",
                icon = when (smart) {
                    SmartList.FRESH -> Icons.Rounded.Schedule
                    SmartList.UNPLAYED -> Icons.Rounded.PlaylistPlay
                    SmartList.MOST -> Icons.Rounded.TrendingUp
                    SmartList.LONG -> Icons.Rounded.Timer
                },
                onClick = { onOpenSmart(smart.key, smart.title) },
            )
        }

        item { SectionLabel("Мои плейлисты") }
        if (playlists.isEmpty()) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Пока пусто", style = MaterialTheme.typography.headlineSmall, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Собери плейлист из любого трека: долгое нажатие → «В плейлист». Или импортируй файл M3U8 кнопкой сверху.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            }
        }
        items(playlists, key = { it.id }) { playlist ->
            PlaylistRow(vm = vm, playlist = playlist, onClick = { onOpen(playlist.id, playlist.name) })
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SmartRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface1),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Amber, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Faint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PlaylistRow(vm: AppVm, playlist: Playlist, onClick: () -> Unit) {
    val samples by produceState<List<Track>>(emptyList(), playlist.id, playlist.trackCount) {
        value = vm.playlistTracks(playlist.id)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistCover(samples)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${playlist.trackCount} треков",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Faint, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun PlaylistCover(tracks: List<Track>, size: androidx.compose.ui.unit.Dp = 48.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2),
        contentAlignment = Alignment.Center,
    ) {
        val four = tracks.take(4)
        if (four.isEmpty()) {
            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Amber.copy(alpha = 0.7f), modifier = Modifier.size(size / 2.4f))
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) {
                    Cover(four.getOrNull(0) ?: four[0], size / 2, 0.dp, Modifier.weight(1f))
                    Cover(four.getOrNull(1) ?: four[0], size / 2, 0.dp, Modifier.weight(1f))
                }
                Row(Modifier.weight(1f)) {
                    Cover(four.getOrNull(2) ?: four[0], size / 2, 0.dp, Modifier.weight(1f))
                    Cover(four.getOrNull(3) ?: four.last(), size / 2, 0.dp, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(
    vm: AppVm,
    currentId: Long?,
    isPlaying: Boolean,
    onPlay: (List<Track>, Int) -> Unit,
    onLongPress: (Track) -> Unit,
) {
    val all by vm.tracks.collectAsStateWithLifecycle()
    val favorites = all.filter { it.favorite }.sortedByDescending { it.dateAdded }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 6.dp)) {
            Text("Избранное", style = MaterialTheme.typography.displayMedium, color = Ink)
            Text("${favorites.size} треков", style = MaterialTheme.typography.bodySmall, color = Muted)
            if (favorites.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoChip(
                        text = "Играть всё",
                        selected = true,
                        onClick = { onPlay(favorites, 0) },
                        leading = { Icon(Icons.Rounded.PlayArrow, null, tint = Amber, modifier = Modifier.size(14.dp)) },
                    )
                    LoChip(
                        text = "Вперемешку",
                        selected = false,
                        onClick = { onPlay(favorites.shuffled(), 0) },
                    )
                }
            }
        }
        if (favorites.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Faint, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Здесь пока тихо", style = MaterialTheme.typography.headlineSmall, color = Ink)
                    Text(
                        "Свайп влево по треку — и он тут",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(favorites, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    isCurrent = track.id == currentId,
                    isPlaying = isPlaying,
                    onClick = { onPlay(favorites, favorites.indexOf(track)) },
                    onLongClick = { onLongPress(track) },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
