package com.oyenter.lo.data

import android.net.Uri

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val path: String,
    val folder: String,
    val mimeType: String,
    val trackNo: Int,
    val size: Long,
    val mediaUri: String,
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val positionMs: Long = 0,
) {
    val uri: Uri get() = Uri.parse(mediaUri)
    val isLong: Boolean get() = durationMs > 10 * 60_000L

    /** Метаданные, которые приходят из MediaStore (без пользовательской статистики). */
    fun sameMeta(other: Track): Boolean =
        title == other.title &&
            artist == other.artist &&
            album == other.album &&
            albumId == other.albumId &&
            durationMs == other.durationMs &&
            dateModified == other.dateModified &&
            path == other.path &&
            mediaUri == other.mediaUri
}

data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val trackCount: Int = 0,
)

enum class SortMode(val key: String, val label: String) {
    DATE_ADDED("date", "Дата добавления"),
    DATE_MODIFIED("modified", "Дата изменения"),
    NAME("name", "Название"),
    ARTIST("artist", "Артист"),
    ALBUM("album", "Альбом"),
    DURATION("duration", "Длительность"),
    PLAY_COUNT("plays", "Прослушивания"),
    LAST_PLAYED("last", "Последний запуск");

    companion object {
        fun fromKey(key: String): SortMode = entries.firstOrNull { it.key == key } ?: DATE_ADDED
    }
}

enum class SmartList(val key: String, val title: String, val subtitle: String) {
    FRESH("fresh", "Свежее", "добавлено за неделю"),
    UNPLAYED("unplayed", "Не слушал", "ни одного запуска"),
    MOST("most", "Часто слушаю", "по количеству запусков"),
    LONG("long", "Долгие", "дольше 10 минут"),
}

data class SyncSummary(
    val total: Int,
    val changed: Int,
    val deleted: Int,
    val durationMs: Long,
    val at: Long = System.currentTimeMillis(),
)

data class PlayerUiState(
    val current: QueueItem? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
)

data class QueueItem(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
) {
    val fakeTrack: Track
        get() = Track(
            id = id, title = title, artist = artist, album = album, albumId = albumId,
            durationMs = durationMs, dateAdded = 0, dateModified = 0, path = "",
            folder = "", mimeType = "audio/*", trackNo = 0, size = 0,
            mediaUri = "content://media/external/audio/media/$id",
        )
}
