package com.oyenter.lo.data

import android.content.Context
import android.net.Uri
import java.io.File

/** Плейлисты M3U8: экспорт в файл и разбор импортированного списка. */
object M3u {

    fun build(name: String, tracks: List<Track>): String = buildString {
        appendLine("#EXTM3U")
        appendLine("#PLAYLIST:$name")
        tracks.forEach { track ->
            appendLine("#EXTINF:${track.durationMs / 1000},${track.artist} - ${track.title}")
            appendLine(if (track.path.isNotBlank()) track.path else track.mediaUri)
        }
    }

    /** Кладёт m3u8 в кэш и отдаёт через FileProvider — для «Поделиться». */
    fun writeCacheFile(context: Context, name: String, content: String): Uri? = runCatching {
        val dir = File(context.cacheDir, "m3u").apply { mkdirs() }
        val safe = name.replace(Regex("[^\\p{L}\\p{N} _-]"), "_").take(60).ifBlank { "playlist" }
        val file = File(dir, "$safe.m3u8")
        file.writeText(content)
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }.getOrNull()

    /** Строки файла без комментариев: пути или content://. */
    fun parse(text: String): List<String> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()

    /** Сопоставляет записи M3U с треками библиотеки: сначала путь, потом имя файла, потом название. */
    fun match(entries: List<String>, tracks: List<Track>): List<Track> {
        if (entries.isEmpty()) return emptyList()
        val byPath = tracks.associateBy { it.path }
        val byName = tracks.associateBy { it.path.substringAfterLast('/').lowercase() }
        val byTitle = tracks.groupBy { it.title.lowercase() }
        val result = LinkedHashMap<Long, Track>()
        entries.forEach { entry ->
            val decoded = Uri.decode(entry)
            val track = byPath[decoded]
                ?: byPath[entry]
                ?: byName[decoded.substringAfterLast('/').lowercase()]
                ?: byTitle[decoded.substringAfterLast('/').substringBeforeLast('.').lowercase()]?.firstOrNull()
            if (track != null) result[track.id] = track
        }
        return result.values.toList()
    }
}
