package com.oyenter.lo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.oyenter.lo.LoApp
import com.oyenter.lo.data.LoDb
import com.oyenter.lo.data.Playlist
import com.oyenter.lo.data.Settings
import com.oyenter.lo.data.SmartList
import com.oyenter.lo.data.SortMode
import com.oyenter.lo.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Filters(
    val query: String = "",
    val sort: SortMode = SortMode.DATE_ADDED,
    val asc: Boolean = false,
    val favoritesOnly: Boolean = false,
    val folder: String? = null,
)

class AppVm(private val app: LoApp) : ViewModel() {

    private val db: LoDb = app.db
    private val lib = app.library

    val settings: StateFlow<Settings> = app.prefs.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val tracks: StateFlow<List<Track>> = lib.tracks
    val syncing: StateFlow<Boolean> = lib.syncing
    val lastSync: StateFlow<com.oyenter.lo.data.SyncSummary?> = lib.summary
    val hasPermission: StateFlow<Boolean> = lib.permission
    val player: StateFlow<com.oyenter.lo.data.PlayerUiState> = app.player.state
    val queue = app.player.queue
    val sleepEndsAt = app.player.sleepEndsAt

    private val _filters = MutableStateFlow(Filters())
    val filters: StateFlow<Filters> = _filters.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val visible: StateFlow<List<Track>> = combine(tracks, _filters, settings) { list, f, s ->
        applyFilters(list, f, s.showHidden)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val continueTrack: StateFlow<Track?> = tracks
        .combine(MutableStateFlow(Unit)) { list, _ -> list.filter { it.lastPlayed > 0 }.maxByOrNull { it.lastPlayed } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val s = settings.first()
            _filters.update { it.copy(sort = SortMode.fromKey(s.sortKey), asc = s.ascending) }
        }
        viewModelScope.launch {
            tracks.collect { refreshPlaylists() }
        }
        refreshPlaylists()
    }

    fun refreshPlaylists() {
        viewModelScope.launch {
            _playlists.value = withContext(Dispatchers.IO) { runCatching { db.playlists() }.getOrElse { emptyList() } }
        }
    }

    // ---------- filters ----------

    fun setQuery(value: String) = _filters.update { it.copy(query = value) }

    fun setSort(mode: SortMode) {
        _filters.update { it.copy(sort = mode) }
        viewModelScope.launch {
            app.prefs.update { it.copy(sortKey = mode.key, ascending = _filters.value.asc) }
        }
    }

    fun toggleAscending() {
        val asc = !_filters.value.asc
        _filters.update { it.copy(asc = asc) }
        viewModelScope.launch { app.prefs.update { it.copy(ascending = asc) } }
    }

    fun toggleFavoritesOnly() = _filters.update { it.copy(favoritesOnly = !it.favoritesOnly) }

    fun setFolder(folder: String?) = _filters.update { it.copy(folder = folder) }

    fun resetFilters() = _filters.update { Filters(sort = it.sort, asc = it.asc) }

    val folders: List<String> get() = tracks.value.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted()

    fun favorites(): List<Track> = tracks.value.filter { it.favorite && !it.hidden }.sortedByDescending { it.dateAdded }

    fun smartTracks(smart: SmartList): List<Track> {
        val all = tracks.value.filter { !it.hidden }
        val now = System.currentTimeMillis()
        return when (smart) {
            SmartList.FRESH -> all.filter { now - it.dateAdded < 7L * 24 * 3600 * 1000 }.sortedByDescending { it.dateAdded }
            SmartList.UNPLAYED -> all.filter { it.playCount == 0 }.sortedByDescending { it.dateAdded }
            SmartList.MOST -> all.filter { it.playCount > 0 }.sortedByDescending { it.playCount }
            SmartList.LONG -> all.filter { it.durationMs > 10 * 60_000L }.sortedByDescending { it.durationMs }
        }
    }

    // ---------- playback ----------

    fun play(list: List<Track>, index: Int) {
        app.player.play(list, index)
    }

    fun toggle() = app.player.toggle()
    fun next() = app.player.next()
    fun previous() = app.player.previous()
    fun seekTo(ms: Long) = app.player.seekTo(ms)

    fun previewSpeed(value: Float) = app.player.previewSpeed(value)

    fun previewPitchSemi(value: Int) = app.player.previewPitchSemi(value)

    fun toggleShuffleForUi() = app.player.toggleShuffle()

    fun cycleRepeatForUi() = app.player.cycleRepeat()

    fun playQueueIndex(index: Int) = app.player.playQueueIndex(index)

    fun removeFromQueue(index: Int) = app.player.removeFromQueue(index)

    fun setSpeed(value: Float) {
        app.player.setSpeed(value)
        viewModelScope.launch { app.prefs.update { it.copy(speed = value) } }
    }

    fun setPitchSemi(value: Int) {
        app.player.setPitchSemi(value)
        viewModelScope.launch { app.prefs.update { it.copy(pitchSemi = value) } }
    }

    fun setPitchLock(value: Boolean) {
        app.player.setPitchLock(value)
        viewModelScope.launch { app.prefs.update { it.copy(pitchLock = value) } }
    }

    fun setExtraVolume(value: Float) {
        app.player.setExtraVolume(value)
        viewModelScope.launch { app.prefs.update { it.copy(extraVolume = value.coerceIn(0f, 2f)) } }
    }

    fun previewExtraVolume(value: Float) = app.player.setExtraVolume(value)

    fun setEq(gains: List<Float>, preamp: Float, enabled: Boolean) {
        app.player.setEq(gains, preamp, enabled)
        viewModelScope.launch { app.prefs.update { it.copy(eqGains = gains, preamp = preamp, eqEnabled = enabled) } }
    }

    fun setSleep(minutes: Int) = app.player.setSleepTimer(minutes)

    fun playNext(track: Track) {
        app.player.playNext(track)
        flash("Играет следующим")
    }

    fun enqueue(track: Track) {
        app.player.enqueue(track)
        flash("Добавлено в очередь")
    }

    // ---------- library ----------

    fun toggleFavorite(track: Track) {
        lib.setFavorite(track.id, !track.favorite)
    }

    fun setHidden(track: Track, hidden: Boolean) {
        lib.setHidden(track.id, hidden)
        flash(if (hidden) "Скрыто. Вернуть — в меню трека" else "Снова в библиотеке")
    }

    fun setShowHidden(value: Boolean) {
        viewModelScope.launch { app.prefs.update { it.copy(showHidden = value) } }
    }

    fun rescan() {
        flash("Сканирую папки…")
        lib.refresh(force = true)
    }

    fun setMinDuration(seconds: Int) {
        viewModelScope.launch { app.prefs.update { it.copy(minDurationSec = seconds) } }
    }

    fun setExcludeSystem(value: Boolean) {
        viewModelScope.launch { app.prefs.update { it.copy(excludeSystem = value) } }
    }

    fun setAccentFromCover(value: Boolean) {
        viewModelScope.launch { app.prefs.update { it.copy(accentFromCover = value) } }
    }

    fun setAutoUpdate(value: Boolean) {
        viewModelScope.launch { app.prefs.update { it.copy(autoUpdate = value) } }
        if (value) lib.refresh(force = true)
    }

    fun setUiScale(value: Float) {
        viewModelScope.launch { app.prefs.update { it.copy(uiScale = value.coerceIn(0.8f, 1.35f)) } }
    }

    fun playShuffled(list: List<Track>) {
        if (list.isEmpty()) return
        app.player.play(list.shuffled(), 0)
    }

    fun setResumeLong(value: Boolean) {
        viewModelScope.launch { app.prefs.update { it.copy(resumeLongTracks = value) } }
        app.player.setResumeLong(value)
    }

    // ---------- playlists ----------

    fun createPlaylist(name: String): Long {
        val id = runCatching { db.createPlaylist(name) }.getOrDefault(-1L)
        refreshPlaylists()
        return id
    }

    fun addToPlaylist(playlistId: Long, ids: List<Long>) {
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) { runCatching { db.addToPlaylist(playlistId, ids) }.getOrDefault(0) }
            refreshPlaylists()
            flash(if (added > 0) "Добавлено: $added" else "Уже в плейлисте")
        }
    }

    fun addToPlaylistByName(name: String, ids: List<Long>) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                val existing = db.playlists().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
                existing?.id ?: db.createPlaylist(name)
            }
            addToPlaylist(id, ids)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { db.deletePlaylist(id) } }
            refreshPlaylists()
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { db.renamePlaylist(id, name) } }
            refreshPlaylists()
        }
    }

    fun removeFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { db.removeFromPlaylist(playlistId, trackId) } }
            refreshPlaylists()
        }
    }

    suspend fun playlistTracks(id: Long): List<Track> =
        withContext(Dispatchers.IO) { runCatching { db.playlistTracks(id) }.getOrElse { emptyList() } }

    suspend fun playlistName(id: Long): String =
        withContext(Dispatchers.IO) { runCatching { db.playlists().firstOrNull { it.id == id }?.name }.getOrNull() ?: "Плейлист" }

    // ---------- misc ----------

    fun flash(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun applyFilters(list: List<Track>, f: Filters, showHidden: Boolean): List<Track> {
        var result = if (showHidden) list else list.filter { !it.hidden }
        val q = f.query.trim().lowercase()
        if (q.isNotEmpty()) {
            result = result.filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    it.album.lowercase().contains(q) ||
                    it.folder.lowercase().contains(q)
            }
        }
        if (f.favoritesOnly) result = result.filter { it.favorite }
        f.folder?.let { folder -> result = result.filter { it.folder == folder } }
        val sorted = when (f.sort) {
            SortMode.DATE_ADDED -> result.sortedBy { it.dateAdded }
            SortMode.DATE_MODIFIED -> result.sortedBy { it.dateModified }
            SortMode.NAME -> result.sortedBy { it.title.lowercase() }
            SortMode.ARTIST -> result.sortedBy { it.artist.lowercase() + it.album.lowercase() + it.trackNo }
            SortMode.ALBUM -> result.sortedBy { it.album.lowercase() + it.trackNo }
            SortMode.DURATION -> result.sortedBy { it.durationMs }
            SortMode.PLAY_COUNT -> result.sortedBy { it.playCount }
            SortMode.LAST_PLAYED -> result.sortedBy { it.lastPlayed }
        }
        return if (f.asc) sorted else sorted.reversed()
    }

    companion object {
        fun factory(app: LoApp) = viewModelFactory {
            initializer { AppVm(app) }
        }
    }
}
