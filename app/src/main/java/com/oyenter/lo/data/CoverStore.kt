package com.oyenter.lo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** Обложки: встроенные в теги, с дисковым и памяти-кэшем. */
object CoverStore {

    /** Меняется при сбросе кэша — Compose перечитывает обложки. */
    var version: Int by mutableIntStateOf(0)
        private set

    private val cache = object : LruCache<String, Bitmap>(28) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /**
     * Ключ обложки: альбом, только если он реально известен.
     * Иначе — сам трек, иначе все «Unknown album» получают одну чужую картинку.
     */
    private fun keyOf(track: Track): String {
        val album = track.album.trim()
        val unknown = album.isEmpty() ||
            album.equals("<unknown>", ignoreCase = true) ||
            album.equals("unknown", ignoreCase = true) ||
            album.equals("неизвестный альбом", ignoreCase = true)
        return if (track.albumId > 0 && !unknown) "a${track.albumId}" else "t${track.id}"
    }

    @Synchronized
    fun load(ctx: Context, track: Track): Bitmap? {
        val key = keyOf(track)
        cache.get(key)?.let { return it }
        val dir = File(ctx.cacheDir, "covers").apply { mkdirs() }
        val file = File(dir, "$key.img")
        val bitmap = when {
            file.exists() -> decodeFile(file) ?: extract(track)?.also { save(it, file) }
            else -> extract(track)?.also { save(it, file) }
        }
        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    /** Сбрасывает кэш конкретного трека — если картинка оказалась чужой. */
    @Synchronized
    fun invalidate(ctx: Context, track: Track) {
        val key = keyOf(track)
        cache.remove(key)
        runCatching { File(File(ctx.cacheDir, "covers"), "$key.img").delete() }
    }

    private fun decodeFile(file: File): Bitmap? =
        runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()

    private fun extract(track: Track): Bitmap? {
        val sources = buildList {
            if (track.path.isNotBlank()) add(track.path)
            add(track.mediaUri)
        }
        for (source in sources) {
            val bitmap = extractFrom(source) ?: continue
            return bitmap
        }
        return null
    }

    private fun extractFrom(source: String): Bitmap? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(source)
            val bytes = mmr.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val largest = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (largest / sample > 720) sample *= 2
            BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun save(bmp: Bitmap, file: File) {
        runCatching {
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        }
    }

    fun clear(ctx: Context) {
        cache.evictAll()
        runCatching { File(ctx.cacheDir, "covers").deleteRecursively() }
        version++
    }

    /** Убирает файлы старой схемы ключей (просто числа), чтобы не подмешивались чужие картинки. */
    fun migrateLegacy(ctx: Context) {
        runCatching {
            File(ctx.cacheDir, "covers").listFiles()?.forEach { file ->
                val name = file.name
                if (!name.startsWith("a") && !name.startsWith("t")) file.delete()
            }
        }
    }
}
