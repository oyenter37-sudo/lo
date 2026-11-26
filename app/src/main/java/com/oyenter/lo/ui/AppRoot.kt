package com.oyenter.lo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oyenter.lo.LoApp
import com.oyenter.lo.data.Track
import com.oyenter.lo.ui.components.LoBackground
import com.oyenter.lo.ui.components.MiniPlayer
import com.oyenter.lo.ui.components.rememberAccent
import com.oyenter.lo.ui.theme.Amber
import com.oyenter.lo.ui.theme.Bg
import com.oyenter.lo.ui.theme.Faint
import com.oyenter.lo.ui.theme.Ink
import com.oyenter.lo.ui.theme.Muted

sealed interface Detail {
    data class Playlist(val id: Long, val title: String) : Detail
    data class Artist(val name: String) : Detail
    data class Album(val album: String, val artist: String) : Detail
    data class Smart(val key: String, val title: String) : Detail
}

sealed interface Sheet {
    data object Sort : Sheet
    data object Equalizer : Sheet
    data object Tuning : Sheet
    data object Queue : Sheet
    data object Sleep : Sheet
    data class TrackMenu(val track: Track) : Sheet
    data class AddToPlaylist(val trackIds: List<Long>) : Sheet
    data object CreatePlaylist : Sheet
    data object Folders : Sheet
}

@Composable
fun AppRoot(app: LoApp) {
    val vm: AppVm = viewModel(factory = AppVm.factory(app))

    val settings by vm.settings.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val visible by vm.visible.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val continueTrack by vm.continueTrack.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val hasPermission by vm.hasPermission.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    val accentSource = player.current?.fakeTrack ?: continueTrack
    val accent = if (settings.accentFromCover) rememberAccent(accentSource) else Amber

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.consumeMessage()
    }

    BackHandler(enabled = playerOpen) { playerOpen = false }
    BackHandler(enabled = !playerOpen && settingsOpen) { settingsOpen = false }
    BackHandler(enabled = !playerOpen && !settingsOpen && detail != null) { detail = null }

    LoBackground(accent = accent) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    if (player.current != null && !playerOpen) {
                        MiniPlayer(
                            state = player,
                            accent = accent,
                            onOpen = { playerOpen = true },
                            onToggle = { vm.toggle() },
                            onNext = { vm.next() },
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    NavigationBar(containerColor = Bg.copy(alpha = 0.94f), tonalElevation = 0.dp) {
                        NavItem("Библиотека", Icons.Rounded.LibraryMusic, tab == 0) {
                            tab = 0
                            detail = null
                        }
                        NavItem("Плейлисты", Icons.Rounded.PlaylistPlay, tab == 1) {
                            tab = 1
                            detail = null
                        }
                        NavItem("Избранное", Icons.Rounded.Favorite, tab == 2) {
                            tab = 2
                            detail = null
                        }
                    }
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                val current = detail
                when {
                    settingsOpen -> SettingsScreen(
                        vm = vm,
                        settings = settings,
                        onBack = { settingsOpen = false },
                    )

                    current != null -> DetailScreen(
                        vm = vm,
                        detail = current,
                        currentId = player.current?.id,
                        isPlaying = player.isPlaying,
                        onBack = { detail = null },
                        onPlay = { list, index -> vm.play(list, index) },
                        onLongPress = { sheet = Sheet.TrackMenu(it) },
                    )

                    tab == 0 -> LibraryScreen(
                        vm = vm,
                        tracks = visible,
                        totalCount = tracks.size,
                        filters = filters,
                        continueTrack = continueTrack,
                        syncing = syncing,
                        hasPermission = hasPermission,
                        currentId = player.current?.id,
                        isPlaying = player.isPlaying,
                        onOpenSettings = { settingsOpen = true },
                        onSort = { sheet = Sheet.Sort },
                        onFolders = { sheet = Sheet.Folders },
                        onLongPress = { sheet = Sheet.TrackMenu(it) },
                        onPlay = { list, index -> vm.play(list, index) },
                    )

                    tab == 1 -> PlaylistsScreen(
                        vm = vm,
                        onOpen = { id, title -> detail = Detail.Playlist(id, title) },
                        onOpenSmart = { key, title -> detail = Detail.Smart(key, title) },
                        onCreate = { sheet = Sheet.CreatePlaylist },
                    )

                    else -> FavoritesScreen(
                        vm = vm,
                        currentId = player.current?.id,
                        isPlaying = player.isPlaying,
                        onPlay = { list, index -> vm.play(list, index) },
                        onLongPress = { sheet = Sheet.TrackMenu(it) },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = playerOpen,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(160)),
        ) {
            PlayerScreen(
                vm = vm,
                settings = settings,
                state = player,
                accent = accent,
                hasNext = player.hasNext,
                onClose = { playerOpen = false },
                onTuning = { sheet = Sheet.Tuning },
                onEqualizer = { sheet = Sheet.Equalizer },
                onQueue = { sheet = Sheet.Queue },
                onSleep = { sheet = Sheet.Sleep },
                onMore = { player.current?.let { q -> tracks.firstOrNull { it.id == q.id } }?.let { sheet = Sheet.TrackMenu(it) } },
            )
        }

        SheetHost(
            sheet = sheet,
            vm = vm,
            settings = settings,
            playlists = playlists,
            onDismiss = { sheet = null },
            onSheet = { sheet = it },
            onDetail = {
                detail = it
                sheet = null
                playerOpen = false
            },
        )
    }
}

@Composable
private fun RowScope.NavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, style = MaterialTheme.typography.bodySmall, color = if (selected) Ink else Faint) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Amber,
            indicatorColor = Amber.copy(alpha = 0.12f),
            unselectedIconColor = Muted,
        ),
    )
}
