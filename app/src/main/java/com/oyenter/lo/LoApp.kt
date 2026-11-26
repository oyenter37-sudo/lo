package com.oyenter.lo

import android.app.Application
import android.os.Build
import android.util.Log
import com.oyenter.lo.data.LibraryRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.oyenter.lo.data.LoDb
import com.oyenter.lo.data.Prefs
import com.oyenter.lo.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LoApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var db: LoDb
    lateinit var prefs: Prefs
    lateinit var library: LibraryRepository
    lateinit var player: PlayerController

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        db = LoDb(this)
        prefs = Prefs(this)
        library = LibraryRepository(this, db, prefs, appScope)
        player = PlayerController(this)
        player.connect()
        appScope.launch {
            prefs.settings.collect { s ->
                player.setResumeLong(s.resumeLongTracks)
                player.applySettings(s)
            }
        }
        library.startObserving()
    }

    /** Пишет стектрейс в Android/data/com.oyenter.lo/files — удобно прислать разработчику. */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val dir = getExternalFilesDir(null) ?: filesDir
                dir.mkdirs()
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val file = File(dir, "crash-${BuildConfig.VERSION_NAME}-$stamp.txt")
                file.writeText(
                    buildString {
                        appendLine("Lo ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT} / ${Build.MODEL}")
                        appendLine("Thread: ${thread.name}")
                        appendLine()
                        appendLine(Log.getStackTraceString(error))
                    },
                )
                Log.e("Lo", "crash saved to ${file.absolutePath}", error)
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
