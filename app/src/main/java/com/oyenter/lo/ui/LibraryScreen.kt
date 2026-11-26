package com.oyenter.lo.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oyenter.lo.data.SortMode
import com.oyenter.lo.data.Track
import com.oyenter.lo.ui.components.Cover
import com.oyenter.lo.ui.components.LoChip
import com.oyenter.lo.ui.components.SectionLabel
import com.oyenter.lo.ui.components.TrackRow
import com.oyenter.lo.ui.components.formatDuration
import com.oyenter.lo.ui.theme.Amber
import com.oyenter.lo.ui.theme.Bg
import com.oyenter.lo.ui.theme.Faint
import com.oyenter.lo.ui.theme.Ink
import com.oyenter.lo.ui.theme.Muted
import com.oyenter.lo.ui.theme.Surface1
import com.oyenter.lo.ui.theme.Surface2
import java.util.Calendar

@Composable
fun LibraryScreen(
    vm: AppVm,
    tracks: List<Track>,
    totalCount: Int,
    filters: Filters,
    continueTrack: Track?,
    syncing: Boolean,
    hasPermission: Boolean,
    currentId: Long?,
    isPlaying: Boolean,
    onOpenSettings: () -> Unit,
    onSort: () -> Unit,
    onFolders: () -> Unit,
    onLongPress: (Track) -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        LibraryHeader(
            totalCount = totalCount,
            syncing = syncing,
            onSearch = { searchOpen = !searchOpen },
            onShuffle = { vm.playShuffled(tracks) },
            onSettings = onOpenSettings,
        )

        AnimatedVisibility(visible = searchOpen, enter = fadeIn(), exit = fadeOut()) {
            SearchField(
                value = filters.query,
                onValueChange = vm::setQuery,
                onClose = {
                    vm.setQuery("")
                    searchOpen = false
                },
            )
        }

        if (!hasPermission) {
            PermissionCard()
            return@Column
        }

        if (continueTrack != null && filters.query.isBlank() && !filters.favoritesOnly) {
            ContinueCard(
                track = continueTrack,
                onPlay = {
                    val list = if (tracks.any { it.id == continueTrack.id }) tracks else listOf(continueTrack)
                    val index = list.indexOfFirst { it.id == continueTrack.id }.coerceAtLeast(0)
                    onPlay(list, index)
                },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LoChip(
                text = filters.sort.label,
                selected = true,
                onClick = onSort,
                leading = {
                    Icon(
                        if (filters.asc) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                        contentDescription = null,
                        tint = Amber,
                        modifier = Modifier.size(13.dp),
                    )
                },
            )
            LoChip(
                text = "Избранное",
                selected = filters.favoritesOnly,
                onClick = vm::toggleFavoritesOnly,
                leading = {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = if (filters.favoritesOnly) Amber else Muted,
                        modifier = Modifier.size(13.dp),
                    )
                },
            )
            LoChip(
                text = filters.folder ?: "Папки",
                selected = filters.folder != null,
                onClick = onFolders,
                leading = {
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = if (filters.folder != null) Amber else Muted,
                        modifier = Modifier.size(13.dp),
                    )
                },
            )
        }

        if (tracks.isEmpty()) {
            EmptyState(
                searching = filters.query.isNotBlank() || filters.favoritesOnly || filters.folder != null,
                onRescan = vm::rescan,
                onReset = vm::resetFilters,
            )
            return@Column
        }

        val rows = remember(tracks, filters.sort, filters.asc) { buildRows(tracks, filters.sort) }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            rows.forEach { row ->
                when (row) {
                    is LibRow.Header -> item(key = "h_${row.title}") { SectionLabel(row.title) }
                    is LibRow.Item -> item(key = row.track.id) {
                        TrackRow(
                            track = row.track,
                            isCurrent = row.track.id == currentId,
                            isPlaying = isPlaying,
                            onClick = { onPlay(tracks, row.index) },
                            onLongClick = { onLongPress(row.track) },
                        )
                    }
                }
            }
            item(key = "tail") { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun LibraryHeader(
    totalCount: Int,
    syncing: Boolean,
    onSearch: () -> Unit,
    onShuffle: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 10.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "Lo",
                    style = MaterialTheme.typography.displayLarge,
                    color = Ink,
                )
                Text(
                    text = "?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Amber,
                    modifier = Modifier.padding(bottom = 7.dp, start = 2.dp),
                )
            }
            Text(
                text = if (totalCount == 0) "пока пусто" else "$totalCount треков на телефоне",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
        if (syncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = Amber,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
        }
        IconButton(onClick = onShuffle) {
            Icon(Icons.Rounded.Shuffle, contentDescription = "Вперемешку", tint = Muted)
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Rounded.Search, contentDescription = "Поиск", tint = Ink)
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Настройки", tint = Ink)
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
            cursorBrush = SolidColor(Amber),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {}),
            decorationBox = { inner ->
                Box {
                    if (value.isBlank()) {
                        Text(
                            "Трек, артист, альбом, папка",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Faint,
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus),
        )
        if (value.isNotBlank()) {
            IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(4.dp))
        Text("закрыть", style = MaterialTheme.typography.bodySmall, color = Faint, modifier = Modifier.clickable(onClick = onClose))
    }
}

@Composable
private fun ContinueCard(track: Track, onPlay: () -> Unit) {
    Surface(
        color = Surface1,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(track, 54.dp, 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("ПРОДОЛЖИТЬ", style = MaterialTheme.typography.labelSmall, color = Amber)
                Spacer(Modifier.height(3.dp))
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${track.artist} · ${formatDuration(track.positionMs.takeIf { it > 0 } ?: track.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Amber, modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun PermissionCard() {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Нужен доступ к музыке", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "Без разрешения Lo не видит треки. Всё остаётся на телефоне — никаких серверов.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
            Spacer(Modifier.height(18.dp))
            Surface(
                color = Amber,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.clickable {
                    runCatching {
                        ctx.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${ctx.packageName}"),
                            ),
                        )
                    }
                },
            ) {
                Text(
                    "Открыть настройки",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = Bg,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(searching: Boolean, onRescan: () -> Unit, onReset: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = Faint, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                if (searching) "Ничего не нашлось" else "Треков пока нет",
                style = MaterialTheme.typography.headlineSmall,
                color = Ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (searching) "Сбрось фильтры или измени запрос" else "Добавь музыку в память телефона и обнови список",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
            Spacer(Modifier.height(16.dp))
            LoChip(
                text = if (searching) "Сбросить фильтры" else "Обновить",
                selected = true,
                onClick = { if (searching) onReset() else onRescan() },
                leading = {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Amber, modifier = Modifier.size(14.dp))
                },
            )
        }
    }
}

private sealed interface LibRow {
    data class Header(val title: String) : LibRow
    data class Item(val track: Track, val index: Int) : LibRow
}

private fun buildRows(tracks: List<Track>, sort: SortMode): List<LibRow> {
    val rows = ArrayList<LibRow>(tracks.size + 12)
    if (sort != SortMode.DATE_ADDED && sort != SortMode.DATE_MODIFIED) {
        rows.add(LibRow.Header("Все треки · ${tracks.size}"))
        tracks.forEachIndexed { index, track -> rows.add(LibRow.Item(track, index)) }
        return rows
    }
    var last = ""
    tracks.forEachIndexed { index, track ->
        val group = dateGroup(track.dateAdded)
        if (group != last) {
            rows.add(LibRow.Header(group))
            last = group
        }
        rows.add(LibRow.Item(track, index))
    }
    return rows
}

private fun dateGroup(timestamp: Long): String {
    if (timestamp <= 0) return "Раньше"
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return "Сегодня"
    now.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (yesterday) return "Вчера"
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 7L * 24 * 3600 * 1000 -> "На этой неделе"
        diff < 30L * 24 * 3600 * 1000 -> "В этом месяце"
        diff < 365L * 24 * 3600 * 1000 -> "В этом году"
        else -> "Раньше"
    }
}
