package com.oyenter.lo.playback

import android.media.audiofx.DynamicsProcessing
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
        sessionId = sid
        rebuild()
    }

    fun update(g: List<Float>, p: Float, on: Boolean) {
        gains = g.take(BANDS).let { if (it.size == BANDS) it else List(BANDS) { 0f } }
        preamp = p
        enabled = on
        if (effect == null && sid() > 0) rebuild() else apply()
    }

    private fun sid() = sessionId

    private fun rebuild() {
        release()
        if (sessionId <= 0) return
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
            val dp = DynamicsProcessing(0, sessionId, config)
            effect = dp
            available = true
            apply()
        } catch (t: Throwable) {
            Log.w("Lo", "equalizer unavailable", t)
            available = false
            effect = null
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
