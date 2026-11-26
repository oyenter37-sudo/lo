package com.oyenter.lo.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryRepository(
    private val ctx: Context,
    private val db: LoDb,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
) {
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _permission = MutableStateFlow(hasPermission())
    val permission: StateFlow<Boolean> = _permission.asStateFlow()

    private var settings = Settings()
    private var syncJob: Job? = null
    private var lastSyncAt = 0L
    private var observerStarted = false

    private val ignoredFolders = setOf(
        "ringtones", "notifications", "alarms", "recordings", "recorder",
        "telegram", "whatsapp", "voice recorder", "call recordings", "callrecorder",
        "tiktok", "snapchat",
    )
    private val ignoredPathParts = listOf("/android/data/", "/android/obb/", "/.nomedia")

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            requestSync()
        }
    }

    init {
        bootstrap()
        scope.launch {
            prefs.settings.collect { fresh ->
                val rebuild = fresh.minDurationSec != settings.minDurationSec ||
                    fresh.excludeSystem != settings.excludeSystem
                settings = fresh
                if (rebuild && hasPermission()) refresh(force = true)
            }
        }
    }

    private fun bootstrap() {
        _permission.value = hasPermission()
        if (!hasPermission()) return
        scope.launch {
            val cached = withContext(Dispatchers.IO) { runCatching { db.readAll() }.getOrElse { emptyList() } }
            if (_tracks.value.isEmpty() && cached.isNotEmpty()) _tracks.value = cached
            refresh(force = true)
        }
    }

    fun hasPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    }

    fun onPermissionChanged() {
        _permission.value = hasPermission()
        if (hasPermission()) refresh(force = true)
    }

    fun startObserving() {
        if (observerStarted || !hasPermission()) return
        observerStarted = true
        runCatching {
            mediaUris().forEach { ctx.contentResolver.registerContentObserver(it, true, observer) }
        }.onFailure { Log.w("Lo", "observer failed", it) }
    }

    fun stopObserving() {
        if (!observerStarted) return
        observerStarted = false
        runCatching { ctx.contentResolver.unregisterContentObserver(observer) }
    }

    /** Быстрый досинк, вызывается при возврате в приложение. */
    fun onResume() {
        if (hasPermission() && settings.autoUpdate) refresh()
    }

    private fun requestSync() {
        if (!hasPermission() || !settings.autoUpdate) return
        scope.launch {
            kotlinx.coroutines.delay(1200)
            refresh()
        }
    }

    fun refresh(force: Boolean = false) {
        if (!force && !settings.autoUpdate) return
        if (!hasPermission()) {
            _permission.value = false
            _tracks.value = emptyList()
            return
        }
        val now = System.currentTimeMillis()
        if (!force && now - lastSyncAt < 1500) return
        syncJob?.cancel()
        syncJob = scope.launch {
            _syncing.value = true
            try {
                val scanned = withContext(Dispatchers.IO) { scan() }
                withContext(Dispatchers.IO) { db.sync(scanned) }
                val all = withContext(Dispatchers.IO) { db.readAll() }
                _tracks.value = all
                _permission.value = true
                lastSyncAt = System.currentTimeMillis()
            } catch (t: Throwable) {
                Log.w("Lo", "sync failed", t)
                _permission.value = hasPermission()
            } finally {
                _syncing.value = false
            }
        }
    }

    // ---------- user actions ----------

    fun setFavorite(id: Long, favorite: Boolean) {
        scope.launch(Dispatchers.IO) { runCatching { db.setFavorite(id, favorite) } }
        _tracks.update { list -> list.map { if (it.id == id) it.copy(favorite = favorite) else it } }
    }

    fun setHidden(id: Long, hidden: Boolean) {
        scope.launch(Dispatchers.IO) { runCatching { db.setHidden(id, hidden) } }
        _tracks.update { list -> list.map { if (it.id == id) it.copy(hidden = hidden) else it } }
    }

    fun notePlayed(id: Long) {
        scope.launch(Dispatchers.IO) { runCatching { db.recordPlay(id) } }
        val now = System.currentTimeMillis()
        _tracks.update { list ->
            list.map { if (it.id == id) it.copy(playCount = it.playCount + 1, lastPlayed = now) else it }
        }
    }

    fun notePosition(id: Long, positionMs: Long) {
        scope.launch(Dispatchers.IO) { runCatching { db.setPosition(id, positionMs) } }
        _tracks.update { list -> list.map { if (it.id == id) it.copy(positionMs = positionMs) else it } }
    }

    // ---------- scanning ----------

    private fun mediaUris(): List<Uri> = if (Build.VERSION.SDK_INT >= 29) {
        runCatching {
            MediaStore.getExternalVolumeNames(ctx).map { MediaStore.Audio.Media.getContentUri(it) }
        }.getOrElse { listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) }
    } else {
        listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
    }

    private fun scan(): List<Track> {
        val minMs = settings.minDurationSec * 1000L
        val found = LinkedHashMap<Long, Track>()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(minMs.toString())
        val base = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val projection = if (Build.VERSION.SDK_INT >= 29) {
            base + MediaStore.Audio.Media.RELATIVE_PATH
        } else base
        val order = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        mediaUris().forEach { collection ->
            runCatching {
                ctx.contentResolver.query(collection, projection, selection, selectionArgs, order)?.use { c ->
                    val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val iModified = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                    val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val iMime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                    val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                    val iSize = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val iRel = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1

                    while (c.moveToNext()) {
                        val id = c.getLong(iId)
                        val path = c.getString(iData) ?: ""
                        val rel = if (iRel >= 0) c.getString(iRel) ?: "" else ""
                        val fileName = c.getString(iName) ?: path.substringAfterLast('/')
                        val folder = folderOf(rel, path)
                        if (!accept(folder, path)) continue

                        val title = (c.getString(iTitle) ?: "").ifBlank {
                            fileName.substringBeforeLast('.').ifBlank { "Без названия" }
                        }
                        val duration = c.getLong(iDur)
                        found[id] = Track(
                            id = id,
                            title = title,
                            artist = (c.getString(iArtist) ?: "").ifBlank { "Неизвестный артист" },
                            album = c.getString(iAlbum) ?: "",
                            albumId = c.getLong(iAlbumId),
                            durationMs = duration,
                            dateAdded = c.getLong(iAdded) * 1000L,
                            dateModified = c.getLong(iModified) * 1000L,
                            path = path,
                            folder = folder,
                            mimeType = c.getString(iMime) ?: "audio/*",
                            trackNo = c.getInt(iTrack),
                            size = c.getLong(iSize),
                            mediaUri = Uri.withAppendedPath(collection, id.toString()).toString(),
                        )
                    }
                }
            }.onFailure { Log.w("Lo", "scan failed for $collection", it) }
        }
        return found.values.sortedByDescending { it.dateAdded }
    }

    private fun folderOf(relativePath: String, absolutePath: String): String {
        val rel = relativePath.trim('/')
        if (rel.isNotEmpty()) return rel.substringAfterLast('/')
        return absolutePath.substringBeforeLast('/', "").substringAfterLast('/')
    }

    private fun accept(folder: String, path: String): Boolean {
        val lowerFolder = folder.lowercase()
        val lowerPath = path.lowercase()
        if (ignoredPathParts.any { lowerPath.contains(it) }) return false
        if (!settings.excludeSystem) return true
        if (lowerFolder in ignoredFolders) return false
        return true
    }
}
