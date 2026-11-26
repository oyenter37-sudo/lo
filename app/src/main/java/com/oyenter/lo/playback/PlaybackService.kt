package com.oyenter.lo.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.oyenter.lo.LoApp
import com.oyenter.lo.MainActivity
import com.oyenter.lo.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private val eq = EqController()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var saver: Job? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as LoApp
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val sessionId = audioManager.generateAudioSessionId()

        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        exo.setAudioSessionId(sessionId)
        player = exo

        session = MediaSession.Builder(this, exo)
            .setSessionActivity(openApp())
            .build()

        scope.launch {
            val s = app.prefs.settings.first()
            val (speed, pitch) = Prefs.applyPitch(s.speed, s.pitchSemi, s.pitchLock)
            exo.playbackParameters = PlaybackParameters(speed, pitch)
            eq.attach(sessionId, s.eqGains, s.preamp, s.eqEnabled)
            EqBridge.onUpdate = { gains, pre, on -> eq.update(gains, pre, on) }
        }

        exo.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                scope.launch {
                    delay(700)
                    val s = app.prefs.settings.first()
                    eq.attach(audioSessionId, s.eqGains, s.preamp, s.eqEnabled)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId?.toLongOrNull() ?: return
                app.library.notePlayed(id)
                savePosition()
                startSaver()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startSaver() else {
                    savePosition()
                    saver?.cancel()
                    saver = null
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) savePosition()
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (player?.isPlaying != true) stopSelf()
    }

    override fun onDestroy() {
        saver?.cancel()
        scope.cancel()
        EqBridge.onUpdate = null
        eq.release()
        session?.release()
        session = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun startSaver() {
        if (saver != null) return
        saver = scope.launch {
            while (isActive) {
                delay(15_000)
                savePosition()
            }
        }
    }

    private fun savePosition() {
        val app = application as LoApp
        val p = player ?: return
        val id = p.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val duration = p.duration
        if (duration < 10 * 60_000L) return
        val position = p.currentPosition
        val value = if (position > duration - 10_000L) 0L else position
        app.library.notePosition(id, value)
    }
}
