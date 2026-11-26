package com.oyenter.lo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File

/**
 * Обложки: только картинка, встроенная в конкретный файл.
 *
 * Раньше кэш группировался по albumId, и все треки «одного альбома» без своей
 * обложки получали чужую картинку. Теперь ключ — всегда id трека: нет в файле
 * встроенной обложки — нет обложки. Никаких заимствований.
 */
object CoverStore {

    /** Меняется при сбросе кэша — Compose перечитывает обложки. */
    var version: Int by mutableIntStateOf(0)
        private set

    private const val PREFIX = "c1_"

    private val cache = object : LruCache<String, Bitmap>(64) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private fun keyOf(track: Track): String = "$PREFIX${track.id}"

    private fun coverFile(ctx: Context, track: Track): File =
        File(File(ctx.cacheDir, "covers").apply { mkdirs() }, "${keyOf(track)}.img")

    @Synchronized
    fun load(ctx: Context, track: Track): Bitmap? {
        val key = keyOf(track)
        cache.get(key)?.let { return it }
        val file = coverFile(ctx, track)
        val bitmap = if (file.exists()) {
            decodeFile(file) ?: extract(track)?.also { save(it, file) }
        } else {
            extract(track)?.also { save(it, file) }
        }
        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    /** URI уже готовой обложки — для нотификации. Null, если картинки нет. */
    fun artworkUri(ctx: Context, track: Track): Uri? = runCatching {
        val file = coverFile(ctx, track)
        if (!file.exists() || file.length() == 0L) return null
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    }.getOrNull()

    /** Сбрасывает кэш конкретного трека. */
    @Synchronized
    fun invalidate(ctx: Context, track: Track) {
        cache.remove(keyOf(track))
        runCatching { coverFile(ctx, track).delete() }
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
        if (source.isBlank()) return null
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(source)
            val bytes = mmr.embeddedPicture ?: return null
            if (bytes.isEmpty()) return null
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
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    fun clear(ctx: Context) {
        cache.evictAll()
        runCatching { File(ctx.cacheDir, "covers").deleteRecursively() }
        version++
    }

    /** Удаляет файлы старых схем (albumId / trackId без префикса), чтобы старые чужие картинки не всплывали. */
    fun migrateLegacy(ctx: Context) {
        runCatching {
            File(ctx.cacheDir, "covers").listFiles()?.forEach { file ->
                if (!file.name.startsWith(PREFIX)) file.delete()
            }
        }
    }
}
