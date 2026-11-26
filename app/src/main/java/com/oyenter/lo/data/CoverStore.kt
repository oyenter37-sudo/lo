package com.oyenter.lo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import java.io.File

/** Обложки: встроенные в теги, с дисковым и памяти-кэшем по альбому. */
object CoverStore {

    private val cache = object : LruCache<Long, Bitmap>(28) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount / 1024
    }

    @Synchronized
    fun load(ctx: Context, track: Track): Bitmap? {
        val key = if (track.albumId > 0) track.albumId else -track.id
        cache.get(key)?.let { return it }
        val dir = File(ctx.cacheDir, "covers").apply { mkdirs() }
        val file = File(dir, "$key.img")
        val bitmap = if (file.exists()) {
            decodeFile(file) ?: extract(track)?.also { save(it, file) }
        } else {
            extract(track)?.also { save(it, file) }
        }
        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    private fun decodeFile(file: File): Bitmap? =
        runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()

    private fun extract(track: Track): Bitmap? {
        if (track.path.isBlank()) return null
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(track.path)
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
    }
}
