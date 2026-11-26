package com.oyenter.lo.playback

import android.media.audiofx.DynamicsProcessing
import android.os.Handler
import android.os.Looper
import android.util.Log

/** Мост из UI в аудио-поток (сервис и UI в одном процессе). */
object EqBridge {
    var onUpdate: ((List<Float>, Float, Boolean) -> Unit)? = null
}

/**
 * 10-полосный эквалайзер через DynamicsProcessing (API 28+).
 * Живёт на аудио-сессии ExoPlayer, поэтому одинаково работает для любого формата
 * и не зависит от вендорских «улучшайзеров».
 */
class EqController {

    companion object {
        val FREQS = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        const val BANDS = 10
    }

    private var effect: DynamicsProcessing? = null
    private val handler = Handler(Looper.getMainLooper())
    private var sessionId = 0
    private var gains: List<Float> = List(BANDS) { 0f }
    private var preamp = 0f
    private var enabled = false

    var available: Boolean = true
        private set

    fun attach(sid: Int, g: List<Float>, p: Float, on: Boolean) {
        gains = g.take(BANDS).let { if (it.size == BANDS) it else List(BANDS) { 0f } }
        preamp = p
        enabled = on
        if (sid != sessionId) {
            sessionId = sid
            rebuild()
        } else {
            apply()
        }
    }

    fun update(g: List<Float>, p: Float, on: Boolean) {
        gains = g.take(BANDS).let { if (it.size == BANDS) it else List(BANDS) { 0f } }
        preamp = p
        enabled = on
        when {
            effect != null -> apply()
            needsEffect() && sessionId > 0 -> rebuild()
        }
    }

    private fun needsEffect(): Boolean = enabled || preamp != 0f || gains.any { it != 0f }

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
        runCatching {
            old.setEnabled(false)
            handler.postDelayed({ runCatching { old.release() } }, 1000)
        }
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
            dp.setInputGainAllChannelsTo(preamp.coerceIn(-12f, 12f))
            dp.setEnabled(enabled)
        } catch (t: Throwable) {
            Log.w("Lo", "equalizer apply failed", t)
            available = false
        }
    }

    fun release() {
        runCatching { effect?.release() }
        effect = null
    }
}
