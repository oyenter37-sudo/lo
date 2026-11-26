package com.oyenter.lo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.loStore: DataStore<Preferences> by preferencesDataStore("lo_settings")

data class Settings(
    val speed: Float = 1f,
    val pitchSemi: Int = 0,
    val pitchLock: Boolean = true,
    val eqEnabled: Boolean = false,
    val eqGains: List<Float> = List(10) { 0f },
    val preamp: Float = 0f,
    val sortKey: String = "date",
    val ascending: Boolean = false,
    val minDurationSec: Int = 20,
    val excludeSystem: Boolean = true,
    val accentFromCover: Boolean = true,
    val resumeLongTracks: Boolean = true,
    val autoUpdate: Boolean = true,
    val showNewDays: Int = 7,
    val uiScale: Float = 1f,
    val showHidden: Boolean = false,
    val extraVolume: Float = 1f,
)

class Prefs(private val ctx: Context) {

    private object K {
        val speed = floatPreferencesKey("speed")
        val pitchSemi = intPreferencesKey("pitch_semi")
        val pitchLock = booleanPreferencesKey("pitch_lock")
        val eqEnabled = booleanPreferencesKey("eq_enabled")
        val eqGains = stringPreferencesKey("eq_gains")
        val preamp = floatPreferencesKey("preamp")
        val sortKey = stringPreferencesKey("sort_key")
        val ascending = booleanPreferencesKey("ascending")
        val minDuration = intPreferencesKey("min_duration")
        val excludeSystem = booleanPreferencesKey("exclude_system")
        val accentFromCover = booleanPreferencesKey("accent_cover")
        val resumeLong = booleanPreferencesKey("resume_long")
        val autoUpdate = booleanPreferencesKey("auto_update")
        val showNewDays = intPreferencesKey("show_new_days")
        val uiScale = floatPreferencesKey("ui_scale")
        val showHidden = booleanPreferencesKey("show_hidden")
        val extraVolume = floatPreferencesKey("extra_volume")
    }

    val settings: Flow<Settings> = ctx.loStore.data.map { it.toSettings() }

    suspend fun update(block: (Settings) -> Settings) {
        ctx.loStore.edit { prefs -> prefs.write(block(prefs.toSettings())) }
    }

    private fun Preferences.toSettings(): Settings {
        val gains = this[K.eqGains]
            ?.split(';')
            ?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 10 }
            ?: List(10) { 0f }
        return Settings(
            speed = this[K.speed] ?: 1f,
            pitchSemi = this[K.pitchSemi] ?: 0,
            pitchLock = this[K.pitchLock] ?: true,
            eqEnabled = this[K.eqEnabled] ?: false,
            eqGains = gains,
            preamp = this[K.preamp] ?: 0f,
            sortKey = this[K.sortKey] ?: "date",
            ascending = this[K.ascending] ?: false,
            minDurationSec = this[K.minDuration] ?: 20,
            excludeSystem = this[K.excludeSystem] ?: true,
            accentFromCover = this[K.accentFromCover] ?: true,
            resumeLongTracks = this[K.resumeLong] ?: true,
            autoUpdate = this[K.autoUpdate] ?: true,
            showNewDays = this[K.showNewDays] ?: 7,
            uiScale = this[K.uiScale] ?: 1f,
            showHidden = this[K.showHidden] ?: false,
            extraVolume = this[K.extraVolume] ?: 1f,
        )
    }

    private fun MutablePreferences.write(s: Settings) {
        this[K.speed] = s.speed
        this[K.pitchSemi] = s.pitchSemi
        this[K.pitchLock] = s.pitchLock
        this[K.eqEnabled] = s.eqEnabled
        this[K.eqGains] = s.eqGains.joinToString(";")
        this[K.preamp] = s.preamp
        this[K.sortKey] = s.sortKey
        this[K.ascending] = s.ascending
        this[K.minDuration] = s.minDurationSec
        this[K.excludeSystem] = s.excludeSystem
        this[K.accentFromCover] = s.accentFromCover
        this[K.resumeLong] = s.resumeLongTracks
        this[K.autoUpdate] = s.autoUpdate
        this[K.showNewDays] = s.showNewDays
        this[K.uiScale] = s.uiScale
        this[K.showHidden] = s.showHidden
        this[K.extraVolume] = s.extraVolume
    }

    companion object {
        /** 0..1 — затухание плеером, >1 — усиление через эквалайзер, дБ. */
        fun boostDb(volume: Float): Float =
            if (volume > 1f) (20.0 * kotlin.math.log10(volume.toDouble())).toFloat().coerceIn(0f, 6.5f) else 0f

        fun applyPitch(speed: Float, pitchSemi: Int, pitchLock: Boolean): Pair<Float, Float> {
            val semis = Math.pow(2.0, pitchSemi / 12.0).toFloat()
            val pitch = semis * (if (pitchLock) 1f else speed)
            return speed.coerceIn(0.5f, 3f) to pitch.coerceIn(0.25f, 4f)
        }
    }
}
