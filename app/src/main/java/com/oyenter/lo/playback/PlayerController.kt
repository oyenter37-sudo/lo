package com.oyenter.lo.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.oyenter.lo.data.CoverStore
import com.oyenter.lo.data.PlayerUiState
import com.oyenter.lo.data.Prefs
import com.oyenter.lo.data.QueueItem
import com.oyenter.lo.data.Track
import com.oyenter.lo.widget.LoWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerController(private val app: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val _sleepEndsAt = MutableStateFlow(0L)
    val sleepEndsAt: StateFlow<Long> = _sleepEndsAt.asStateFlow()

    private var speed = 1f
    private var pitchSemi = 0
    private var pitchLock = true
    private var resumeLong = true
    private var extraVolume = 1f
    private var sleepJob: Job? = null
    private var ticker: Job? = null
    private var paramJob: Job? = null
    private var connecting = false
    private var widgetKey = ""
    private val pending = ArrayDeque<(MediaController) -> Unit>()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync()
        }
    }

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            connecting = false
            val c = runCatching { future.get() }.getOrNull()
            if (c == null) return@addListener
            controller = c
            c.addListener(listener)
            runCatching { c.volume = extraVolume.coerceAtMost(1f) }
            applyParamsNow()
            while (pending.isNotEmpty()) {
                runCatching { pending.removeFirst().invoke(c) }
            }
            sync()
            startTicker()
        }, ContextCompat.getMainExecutor(app))
    }

    /**
     * Все действия идут через это: если контроллер ещё подключается,
     * нажатие не теряется, а выполняется сразу после коннекта.
     */
    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) {
            runCatching { block(c) }
                .onFailure { android.util.Log.w("Lo", "player action failed", it) }
        } else {
            pending.addLast(block)
            connect()
        }
    }

    // ---------- playback ----------

    fun play(tracks: List<Track>, index: Int) {
        if (tracks.isEmpty()) return
        val safeIndex = index.coerceIn(tracks.indices)
        val track = tracks[safeIndex]
        val startAt = if (resumeLong && track.isLong && track.positionMs in 5_000 until (track.durationMs - 10_000)) {
            track.positionMs
        } else 0L
        withController { c ->
            c.setMediaItems(tracks.map { it.toMediaItem() }, safeIndex, startAt)
            c.prepare()
            c.play()
        }
    }

    fun playQueue(items: List<QueueItem>, index: Int) {
        if (items.isEmpty()) return
        play(items.map { it.fakeTrack }, index)
    }

    fun toggle() = withController { c ->
        when {
            c.isPlaying -> c.pause()
            c.mediaItemCount == 0 -> Unit
            c.playbackState == Player.STATE_ENDED -> {
                c.seekToDefaultPosition()
                c.play()
            }
            c.playbackState == Player.STATE_IDLE -> {
                c.prepare()
                c.play()
            }
            else -> c.play()
        }
    }

    fun next() = withController { c -> c.seekToNextMediaItem() }

    fun previous() = withController { c ->
        if (c.currentPosition > 3_000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) = withController { c -> c.seekTo(positionMs.coerceAtLeast(0)) }

    fun seekBy(deltaMs: Long) = withController { c ->
        val duration = c.duration.takeIf { it > 0 } ?: 0
        var target = (c.currentPosition + deltaMs).coerceAtLeast(0)
        if (duration > 0) target = target.coerceAtMost(duration)
        c.seekTo(target)
    }

    fun playNext(track: Track) = withController { c ->
        val index = if (c.mediaItemCount == 0) 0 else c.currentMediaItemIndex + 1
        c.addMediaItem(index, track.toMediaItem())
    }

    fun enqueue(track: Track) = withController { c -> c.addMediaItem(track.toMediaItem()) }

    fun playQueueIndex(index: Int) = withController { c -> c.seekToDefaultPosition(index) }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun stop() = controller?.stop()

    // ---------- tuning ----------

    fun setSpeed(value: Float) {
        speed = value.coerceIn(0.5f, 3f)
        applyParamsNow()
    }

    fun previewSpeed(value: Float) {
        speed = value.coerceIn(0.5f, 3f)
        applyParams()
    }

    fun setPitchSemi(value: Int) {
        pitchSemi = value.coerceIn(-12, 12)
        applyParamsNow()
    }

    fun previewPitchSemi(value: Int) {
        pitchSemi = value.coerceIn(-12, 12)
        applyParams()
    }

    fun setPitchLock(value: Boolean) {
        pitchLock = value
        applyParamsNow()
    }

    fun setResumeLong(value: Boolean) {
        resumeLong = value
    }

    fun setEq(gains: List<Float>, preamp: Float, enabled: Boolean) {
        runCatching { EqBridge.onUpdate?.invoke(gains, preamp, enabled, Prefs.boostDb(extraVolume)) }
            .onFailure { android.util.Log.w("Lo", "eq bridge failed", it) }
    }

    /** 0..2: до 100% — тише, выше — усиление через лимитер. */
    fun setExtraVolume(value: Float) {
        extraVolume = value.coerceIn(0f, 2f)
        runCatching { controller?.volume = extraVolume.coerceAtMost(1f) }
        runCatching { EqBridge.onUpdate?.invoke(lastGains, lastPreamp, lastEqOn, Prefs.boostDb(extraVolume)) }
    }

    private var lastGains: List<Float> = List(10) { 0f }
    private var lastPreamp = 0f
    private var lastEqOn = false

    fun applySettings(s: com.oyenter.lo.data.Settings) {
        speed = s.speed
        pitchSemi = s.pitchSemi
        pitchLock = s.pitchLock
        resumeLong = s.resumeLongTracks
        extraVolume = s.extraVolume
        lastGains = s.eqGains
        lastPreamp = s.preamp
        lastEqOn = s.eqEnabled
        applyParamsNow()
        runCatching { controller?.volume = extraVolume.coerceAtMost(1f) }
        setEq(s.eqGains, s.preamp, s.eqEnabled)
    }

    /**
     * Дёргать параметры на каждый пиксель слайдера нельзя: Sonic пересобирается
     * и звук проваливается. Превью идёт с задержкой, финальное значение — сразу.
     */
    private fun applyParams() {
        paramJob?.cancel()
        paramJob = scope.launch {
            delay(60)
            applyParamsNow()
        }
    }

    fun applyParamsNow() {
        val (sp, pitch) = Prefs.applyPitch(speed, pitchSemi, pitchLock)
        withController { c ->
            c.playbackParameters = PlaybackParameters(sp, pitch)
            c.volume = extraVolume.coerceAtMost(1f)
            kickIfStalled(c)
        }
    }

    /** Страховка: если после смены параметров звук «провалился», подталкиваем позицию. */
    private fun kickIfStalled(c: MediaController) {
        val startPosition = c.currentPosition
        scope.launch {
            delay(1200)
            if (!c.isPlaying) return@launch
            val moved = kotlin.math.abs(c.currentPosition - startPosition)
            if (moved < 40L) {
                runCatching { c.seekTo(startPosition) }
            }
        }
    }

    // ---------- sleep timer ----------

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes <= 0) {
            _sleepEndsAt.value = 0L
            return
        }
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _sleepEndsAt.value = endsAt
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            _sleepEndsAt.value = 0L
        }
    }

    // ---------- internals ----------

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val c = controller
                if (c != null && c.isPlaying) {
                    _state.update {
                        it.copy(
                            positionMs = c.currentPosition.coerceAtLeast(0),
                            durationMs = if (c.duration > 0) c.duration else it.durationMs,
                        )
                    }
                }
                delay(300)
            }
        }
    }

    private fun sync() {
        val c = controller ?: return
        val item = c.currentMediaItem?.toQueueItem()
        _state.update {
            it.copy(
                current = item,
                isPlaying = c.isPlaying,
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = if (c.duration > 0) c.duration else (item?.durationMs ?: 0),
                shuffle = c.shuffleModeEnabled,
                repeat = c.repeatMode,
                hasNext = c.hasNextMediaItem(),
                hasPrevious = c.hasPreviousMediaItem(),
            )
        }
        val count = c.mediaItemCount
        _queue.value = List(count) { c.getMediaItemAt(it).toQueueItem() }

        val key = "${item?.id}:${c.isPlaying}"
        if (key != widgetKey) {
            widgetKey = key
            LoWidget.refresh(app)
        }
    }

    private fun Track.toMediaItem(): MediaItem {
        val extras = Bundle().apply {
            putLong("duration", durationMs)
            putLong("albumId", albumId)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(CoverStore.artworkUri(app, this))
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(mediaUri)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun MediaItem.toQueueItem(): QueueItem {
        val md = mediaMetadata
        val extras = md.extras
        return QueueItem(
            id = mediaId.toLongOrNull() ?: 0L,
            title = md.title?.toString()?.ifBlank { "Без названия" } ?: "Без названия",
            artist = md.artist?.toString().orEmpty(),
            album = md.albumTitle?.toString().orEmpty(),
            albumId = extras?.getLong("albumId") ?: 0L,
            durationMs = extras?.getLong("duration") ?: 0L,
        )
    }
}
