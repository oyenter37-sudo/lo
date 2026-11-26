package com.oyenter.lo.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oyenter.lo.data.SmartList
import com.oyenter.lo.data.Track
import com.oyenter.lo.ui.components.TrackRow
import com.oyenter.lo.ui.theme.Amber
import com.oyenter.lo.ui.theme.Bg
import com.oyenter.lo.ui.theme.Ink
import com.oyenter.lo.ui.theme.Muted
import com.oyenter.lo.ui.theme.Surface1

@Composable
fun DetailScreen(
    vm: AppVm,
    detail: Detail,
    currentId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onLongPress: (Track) -> Unit,
) {
    val all by vm.tracks.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var tracks by remember(detail) { mutableStateOf<List<Track>>(emptyList()) }
    var title by remember(detail) {
        mutableStateOf(
            when (detail) {
                is Detail.Playlist -> detail.title
                is Detail.Artist -> detail.name
                is Detail.Album -> detail.album
                is Detail.Smart -> detail.title
            },
        )
    }
    var subtitle by remember(detail) { mutableStateOf("") }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(detail, all) {
        when (detail) {
            is Detail.Playlist -> {
                tracks = vm.playlistTracks(detail.id)
                title = vm.playlistName(detail.id)
                subtitle = "${tracks.size} треков"
            }

            is Detail.Artist -> {
                tracks = all.filter { it.artist.equals(detail.name, ignoreCase = true) }
                    .sortedWith(compareBy({ it.album.lowercase() }, { it.trackNo }))
                subtitle = "${tracks.size} треков · ${tracks.map { it.album }.distinct().size} альбомов"
            }

            is Detail.Album -> {
                tracks = all.filter {
                    it.album.equals(detail.album, ignoreCase = true) && it.artist.equals(detail.artist, ignoreCase = true)
                }.sortedBy { it.trackNo }
                subtitle = "${detail.artist} · ${tracks.size} треков"
            }

            is Detail.Smart -> {
                val smart = SmartList.entries.firstOrNull { it.key == detail.key } ?: SmartList.FRESH
                tracks = vm.smartTracks(smart)
                subtitle = "${tracks.size} треков"
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 10.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Назад", tint = Ink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            if (detail is Detail.Playlist) {
                IconButton(onClick = { vm.exportPlaylist(ctx, detail.id) }) {
                    Icon(Icons.Rounded.Share, contentDescription = "Экспорт M3U8", tint = Muted, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { showRename = true }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Переименовать", tint = Muted, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { showDelete = true }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Удалить", tint = Muted, modifier = Modifier.size(18.dp))
                }
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = Amber,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.clickable { if (tracks.isNotEmpty()) onPlay(tracks, 0) },
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Bg, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Играть всё", style = MaterialTheme.typography.titleMedium, color = Bg)
                }
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                color = Surface1,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.clickable { if (tracks.isNotEmpty()) onPlay(tracks.shuffled(), 0) },
            ) {
                Text(
                    "Вперемешку",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink,
                )
            }
        }

        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Пусто", style = MaterialTheme.typography.headlineSmall, color = Muted)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        isCurrent = track.id == currentId,
                        isPlaying = isPlaying,
                        onClick = { onPlay(tracks, tracks.indexOf(track)) },
                        onLongClick = { onLongPress(track) },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showRename && detail is Detail.Playlist) {
        var name by remember { mutableStateOf(title) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            containerColor = Surface1,
            title = { Text("Название плейлиста", color = Ink) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renamePlaylist(detail.id, name)
                    showRename = false
                }) { Text("Сохранить", color = Amber) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Отмена", color = Muted) }
            },
        )
    }

    if (showDelete && detail is Detail.Playlist) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = Surface1,
            title = { Text("Удалить плейлист?", color = Ink) },
            text = { Text("Треки останутся в библиотеке.", color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePlaylist(detail.id)
                    showDelete = false
                    onBack()
                }) { Text("Удалить", color = Amber) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Отмена", color = Muted) }
            },
        )
    }
}
