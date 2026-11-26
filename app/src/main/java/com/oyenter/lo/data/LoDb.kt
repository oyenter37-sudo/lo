package com.oyenter.lo.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class LoDb(context: Context) : SQLiteOpenHelper(context.applicationContext, "lo.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE tracks(
                id INTEGER PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT NOT NULL,
                albumId INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                dateAdded INTEGER NOT NULL,
                dateModified INTEGER NOT NULL,
                path TEXT NOT NULL,
                folder TEXT NOT NULL,
                mimeType TEXT NOT NULL,
                trackNo INTEGER NOT NULL,
                size INTEGER NOT NULL,
                mediaUri TEXT NOT NULL,
                playCount INTEGER NOT NULL DEFAULT 0,
                lastPlayed INTEGER NOT NULL DEFAULT 0,
                favorite INTEGER NOT NULL DEFAULT 0,
                hidden INTEGER NOT NULL DEFAULT 0,
                positionMs INTEGER NOT NULL DEFAULT 0
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX idx_tracks_date_added ON tracks(dateAdded)")
        db.execSQL("CREATE INDEX idx_tracks_folder ON tracks(folder)")
        db.execSQL("CREATE TABLE playlists(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE playlist_tracks(playlistId INTEGER NOT NULL, trackId INTEGER NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlistId, trackId))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            runCatching { db.execSQL("ALTER TABLE tracks ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0") }
        }
    }

    // ---------- tracks ----------

    fun readAll(): List<Track> {
        val out = ArrayList<Track>(512)
        readableDatabase.query("tracks", null, null, null, null, null, "dateAdded DESC").use { c ->
            while (c.moveToNext()) out.add(c.toTrack())
        }
        return out
    }

    fun track(id: Long): Track? =
        readableDatabase.query("tracks", null, "id=?", arrayOf(id.toString()), null, null, null).use { c ->
            if (c.moveToFirst()) c.toTrack() else null
        }

    fun count(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM tracks", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /** Возвращает число изменённых записей. Статистика и избранное сохраняются. */
    fun sync(scanned: List<Track>): Int {
        val db = writableDatabase
        val existing = readAll().associateBy { it.id }
        val incoming = scanned.mapTo(HashSet()) { it.id }
        var changed = 0
        db.beginTransaction()
        try {
            scanned.forEach { fresh ->
                val old = existing[fresh.id]
                if (old != null && old.sameMeta(fresh)) return@forEach
                val merged = if (old == null) fresh else fresh.copy(
                    playCount = old.playCount,
                    lastPlayed = old.lastPlayed,
                    favorite = old.favorite,
                    hidden = old.hidden,
                    positionMs = old.positionMs,
                )
                db.insertWithOnConflict("tracks", null, merged.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
                changed++
            }
            (existing.keys - incoming).chunked(400).forEach { chunk ->
                db.delete("tracks", "id IN (${chunk.joinToString(",")})", null)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return changed
    }

    /** Записывает только изменившиеся треки и удаляет пропавшие. */
    fun applyDelta(changed: List<Track>, deleted: Set<Long>) {
        if (changed.isEmpty() && deleted.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            changed.forEach { fresh ->
                val old = track(fresh.id)
                val merged = if (old == null) fresh else fresh.copy(
                    playCount = old.playCount,
                    lastPlayed = old.lastPlayed,
                    favorite = old.favorite,
                    hidden = old.hidden,
                    positionMs = old.positionMs,
                )
                db.insertWithOnConflict("tracks", null, merged.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
            }
            deleted.chunked(400).forEach { chunk ->
                db.delete("tracks", "id IN (${chunk.joinToString(",")})", null)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun setHidden(id: Long, hidden: Boolean) {
        val cv = ContentValues().apply { put("hidden", if (hidden) 1 else 0) }
        writableDatabase.update("tracks", cv, "id=?", arrayOf(id.toString()))
    }

    fun setFavorite(id: Long, favorite: Boolean) {
        val cv = ContentValues().apply { put("favorite", if (favorite) 1 else 0) }
        writableDatabase.update("tracks", cv, "id=?", arrayOf(id.toString()))
    }

    fun recordPlay(id: Long) {
        writableDatabase.execSQL(
            "UPDATE tracks SET playCount=playCount+1, lastPlayed=? WHERE id=?",
            arrayOf(System.currentTimeMillis(), id),
        )
    }

    fun setPosition(id: Long, positionMs: Long) {
        val cv = ContentValues().apply { put("positionMs", positionMs.coerceAtLeast(0)) }
        writableDatabase.update("tracks", cv, "id=?", arrayOf(id.toString()))
    }

    // ---------- playlists ----------

    fun playlists(): List<Playlist> {
        val out = ArrayList<Playlist>()
        readableDatabase.rawQuery(
            "SELECT p.id, p.name, p.createdAt, (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId=p.id) " +
                "FROM playlists p ORDER BY p.createdAt DESC",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out.add(Playlist(c.getLong(0), c.getString(1) ?: "Плейлист", c.getLong(2), c.getInt(3)))
            }
        }
        return out
    }

    fun createPlaylist(name: String): Long {
        val cv = ContentValues().apply {
            put("name", name.trim().ifBlank { "Плейлист" })
            put("createdAt", System.currentTimeMillis())
        }
        return writableDatabase.insertOrThrow("playlists", null, cv)
    }

    fun renamePlaylist(id: Long, name: String) {
        val cv = ContentValues().apply { put("name", name.trim().ifBlank { "Плейлист" }) }
        writableDatabase.update("playlists", cv, "id=?", arrayOf(id.toString()))
    }

    fun deletePlaylist(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("playlist_tracks", "playlistId=?", arrayOf(id.toString()))
            db.delete("playlists", "id=?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun playlistTracks(playlistId: Long): List<Track> {
        val out = ArrayList<Track>()
        readableDatabase.rawQuery(
            "SELECT t.* FROM tracks t JOIN playlist_tracks pt ON pt.trackId=t.id " +
                "WHERE pt.playlistId=? ORDER BY pt.position ASC",
            arrayOf(playlistId.toString()),
        ).use { c -> while (c.moveToNext()) out.add(c.toTrack()) }
        return out
    }

    fun addToPlaylist(playlistId: Long, ids: List<Long>): Int {
        val db = writableDatabase
        var pos = db.rawQuery(
            "SELECT IFNULL(MAX(position), -1) FROM playlist_tracks WHERE playlistId=?",
            arrayOf(playlistId.toString()),
        ).use { if (it.moveToFirst()) it.getInt(0) else -1 } + 1
        var added = 0
        db.beginTransaction()
        try {
            ids.forEach { trackId ->
                val cv = ContentValues().apply {
                    put("playlistId", playlistId)
                    put("trackId", trackId)
                    put("position", pos)
                }
                if (db.insertWithOnConflict("playlist_tracks", null, cv, SQLiteDatabase.CONFLICT_IGNORE) != -1L) {
                    pos++
                    added++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return added
    }

    fun removeFromPlaylist(playlistId: Long, trackId: Long) {
        writableDatabase.delete(
            "playlist_tracks",
            "playlistId=? AND trackId=?",
            arrayOf(playlistId.toString(), trackId.toString()),
        )
    }

    // ---------- mapping ----------

    private fun Cursor.toTrack(): Track {
        fun s(name: String): String = getString(getColumnIndexOrThrow(name)) ?: ""
        fun l(name: String): Long = getLong(getColumnIndexOrThrow(name))
        fun i(name: String): Int = getInt(getColumnIndexOrThrow(name))
        return Track(
            id = l("id"),
            title = s("title").ifBlank { "Без названия" },
            artist = s("artist").ifBlank { "Неизвестный артист" },
            album = s("album"),
            albumId = l("albumId"),
            durationMs = l("durationMs"),
            dateAdded = l("dateAdded"),
            dateModified = l("dateModified"),
            path = s("path"),
            folder = s("folder"),
            mimeType = s("mimeType"),
            trackNo = i("trackNo"),
            size = l("size"),
            mediaUri = s("mediaUri"),
            playCount = i("playCount"),
            lastPlayed = l("lastPlayed"),
            favorite = i("favorite") != 0,
            hidden = i("hidden") != 0,
            positionMs = l("positionMs"),
        )
    }

    private fun Track.toValues(): ContentValues = ContentValues(20).apply {
        put("id", id)
        put("title", title)
        put("artist", artist)
        put("album", album)
        put("albumId", albumId)
        put("durationMs", durationMs)
        put("dateAdded", dateAdded)
        put("dateModified", dateModified)
        put("path", path)
        put("folder", folder)
        put("mimeType", mimeType)
        put("trackNo", trackNo)
        put("size", size)
        put("mediaUri", mediaUri)
        put("playCount", playCount)
        put("lastPlayed", lastPlayed)
        put("favorite", if (favorite) 1 else 0)
        put("hidden", if (hidden) 1 else 0)
        put("positionMs", positionMs)
    }
}
