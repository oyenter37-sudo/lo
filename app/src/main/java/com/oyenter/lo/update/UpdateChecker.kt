package com.oyenter.lo.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.oyenter.lo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Обновления через GitHub Releases: никакого сервера, только публичный API.
 * APK качается системным DownloadManager — дальше обычная установка поверх.
 */
object UpdateChecker {

    private const val API = "https://api.github.com/repos/oyenter37-sudo/lo/releases/latest"

    data class Info(
        val version: String,
        val apkUrl: String,
        val notes: String,
        val sizeMb: Long,
    )

    suspend fun check(): Result<Info?> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Lo/${BuildConfig.VERSION_NAME}")
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tag = json.optString("tag_name").removePrefix("v").removePrefix("V")
                if (tag.isBlank() || !isNewer(tag, BuildConfig.VERSION_NAME)) return@runCatching null

                val assets = json.optJSONArray("assets") ?: return@runCatching null
                var url: String? = null
                var size = 0L
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        url = asset.optString("browser_download_url")
                        size = asset.optLong("size")
                        break
                    }
                }
                if (url.isNullOrBlank()) return@runCatching null

                Info(
                    version = tag,
                    apkUrl = url,
                    notes = json.optString("body").take(600),
                    sizeMb = size / 1024 / 1024,
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    fun download(context: Context, info: Info): Boolean = runCatching {
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("Lo ${info.version}")
            .setDescription("Обновление плеера")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "Lo-${info.version}.apk",
            )
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        true
    }.getOrDefault(false)

    fun isNewer(remote: String, local: String): Boolean {
        val a = remote.split('.', '-').mapNotNull { it.toIntOrNull() }
        val b = local.split('.', '-').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
