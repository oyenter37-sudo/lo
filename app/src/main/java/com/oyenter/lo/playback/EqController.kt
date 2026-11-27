package com.oyenter.lo.playback

import android.media.audiofx.DynamicsProcessing
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Мост из UI в аудио-поток (сервис и UI в одном процессе). */
object EqBridge {
    var onUpdate: ((List<Float>, Float, Boolean, Float) -> Unit)? = null
}

/**
 * 10-полосный эквалайзер через DynamicsProcessing (API 28+).
 *
 * ВАЖНО: все вызовы эффекта идут на отдельном потоке. DynamicsProcessing умеет
 * подвисать при пересоздании аудиосессии (например, при смене тона), и если это
 * случается в главном потоке сервиса, до него перестают доходить команды
 * play/pause — кнопка «умирает» до перезапуска движка. Теперь очередь команд
 * никогда не блокируется эффектом.
 */
class EqController {

    companion object {
        val FREQS = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        const val BANDS = 10
    }

    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "lo-eq").apply { isDaemon = true }
    }
    private val closer = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "lo-eq-close").apply { isDaemon = true }
    }

    private var effect: DynamicsProcessing? = null
    private var sessionId = 0
    @Volatile private var gains: List<Float> = List(BANDS) { 0f }
    @Volatile private var preamp = 0f
    @Volatile private var boost = 0f
    @Volatile private var enabled = false

    @Volatile
    var available: Boolean = true
        private set

    private val drainScheduled = AtomicBoolean(false)
    private val dirty = AtomicBoolean(false)

    fun attach(sid: Int, g: List<Float>, p: Float, on: Boolean, volumeBoostDb: Float = 0f) {
        store(g, p, on, volumeBoostDb)
        worker.execute {
            val sessionChanged = sid != sessionId
            sessionId = sid
            if (sessionChanged) rebuild() else apply()
        }
    }

    fun update(g: List<Float>, p: Float, on: Boolean, volumeBoostDb: Float = 0f) {
        store(g, p, on, volumeBoostDb)
        // Слайдер шлёт десятки обновлений — выполняем только последнее.
        dirty.set(true)
        if (drainScheduled.compareAndSet(false, true)) {
            worker.execute { drain() }
        }
    }

    private fun store(g: List<Float>, p: Float, on: Boolean, volumeBoostDb: Float) {
        gains = g.take(BANDS).let { if (it.size == BANDS) it else List(BANDS) { 0f } }
        preamp = p
        boost = volumeBoostDb
        enabled = on
    }

    private fun drain() {
        drainScheduled.set(false)
        if (!dirty.compareAndSet(true, false)) return
        when {
            !needsEffect() -> {
                // Эффект больше не нужен — отпускаем, чтобы лимитер не висел в цепи.
                val old = effect
                effect = null
                releaseLater(old)
            }
            effect != null -> apply()
            sessionId > 0 -> rebuild()
        }
    }

    private fun needsEffect(): Boolean =
        enabled || preamp != 0f || boost > 0.1f || gains.any { it != 0f }

    private fun rebuild() {
        val old = effect
        effect = null
        if (!needsEffect() || sessionId <= 0) {
            releaseLater(old)
            return
        }
        try {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                true,
                BANDS,
                false,
                1,
                false,
                1,
                true,
            ).build()
            effect = DynamicsProcessing(0, sessionId, config)
            available = true
            if (boost > 0.1f) {
                runCatching {
                    effect?.setLimiterAllChannelsTo(
                        DynamicsProcessing.Limiter(true, true, 0, 0.005f, 0.06f, 20f, -1f, 0f),
                    )
                }
            }
            apply()
            releaseLater(old)
        } catch (t: Throwable) {
            Log.w("Lo", "equalizer unavailable", t)
            available = false
            effect = null
            releaseLater(old)
        }
    }

    /** Старый эффект отпускаем не сразу: аудиопоток ещё может его читать. */
    private fun releaseLater(old: DynamicsProcessing?) {
        if (old == null) return
        runCatching { old.setEnabled(false) }
        closer.schedule({ runCatching { old.release() } }, 1, TimeUnit.SECONDS)
    }

    private fun apply() {
        val dp = effect ?: return
        try {
            for (i in 0 until BANDS) {
                dp.setPreEqBandAllChannelsTo(
                    i,
                    DynamicsProcessing.EqBand(true, FREQS[i].toFloat(), gains[i].coerceIn(-12f, 12f)),
                )
            }
            dp.setInputGainAllChannelsTo((preamp + boost).coerceIn(-12f, 12f))
            dp.setEnabled(enabled)
        } catch (t: Throwable) {
            Log.w("Lo", "equalizer apply failed", t)
            available = false
        }
    }

    /** Полный сброс — вызывается при аварийном перезапуске движка. */
    fun release() {
        worker.execute {
            val old = effect
            effect = null
            runCatching { old?.setEnabled(false) }
            runCatching { old?.release() }
        }
    }
}
