package com.baijum.thaalam.data

import android.content.Context
import com.baijum.thaalam.domain.PresetCodec

/** User-interface preferences that are not part of the audio configuration. */
data class UiPreferences(
    val hapticsEnabled: Boolean = true,
    val flashEnabled: Boolean = false,
    val keepScreenOn: Boolean = true,
    val visualLatencyMs: Int = 0,
)

/** Persists the live metronome configuration as shared JSON. */
class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): MetronomeSettings =
        PresetCodec.decodeSettings(prefs.getString(KEY_SETTINGS, null))

    fun save(settings: MetronomeSettings) {
        prefs.edit().putString(KEY_SETTINGS, PresetCodec.encodeSettings(settings)).apply()
    }

    fun loadUiPreferences(): UiPreferences = UiPreferences(
        hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true),
        flashEnabled = prefs.getBoolean(KEY_FLASH, false),
        keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
        visualLatencyMs = prefs.getInt(KEY_VISUAL_LATENCY, 0).coerceIn(0, 300),
    )

    fun saveUiPreferences(preferences: UiPreferences) {
        prefs.edit()
            .putBoolean(KEY_HAPTICS, preferences.hapticsEnabled)
            .putBoolean(KEY_FLASH, preferences.flashEnabled)
            .putBoolean(KEY_KEEP_SCREEN_ON, preferences.keepScreenOn)
            .putInt(KEY_VISUAL_LATENCY, preferences.visualLatencyMs.coerceIn(0, 300))
            .apply()
    }

    private companion object {
        const val PREFS = "thaalam_settings"
        const val KEY_SETTINGS = "metronome_settings_v1"
        const val KEY_HAPTICS = "haptics"
        const val KEY_FLASH = "flash"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_VISUAL_LATENCY = "visual_latency_ms"
    }
}
