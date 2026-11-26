package com.oyenter.lo

import android.app.Application
import com.oyenter.lo.data.LibraryRepository
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
}
