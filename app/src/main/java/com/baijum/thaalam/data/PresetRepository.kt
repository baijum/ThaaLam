package com.baijum.thaalam.data

import android.content.Context
import com.baijum.thaalam.domain.PresetCodec
import com.baijum.thaalam.domain.PresetFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Persists the user's saved metronome presets. */
class PresetRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _presets = MutableStateFlow<List<MetronomePreset>>(emptyList())
    val presets: StateFlow<List<MetronomePreset>> = _presets.asStateFlow()

    init {
        // Load after the StateFlow exists: load() must never touch _presets
        // during property initialisation.
        _presets.value = load()
    }

    private fun load(): List<MetronomePreset> {
        val raw = prefs.getString(KEY_PRESETS, null)
        val stored = PresetCodec.decodePresets(raw)
        if (stored.isNotEmpty() || prefs.getBoolean(KEY_SEEDED, false)) return stored
        val seeded = starterPresets()
        prefs.edit()
            .putString(KEY_PRESETS, PresetCodec.encodePresets(seeded))
            .putBoolean(KEY_SEEDED, true)
            .apply()
        return seeded
    }

    fun add(name: String, settings: MetronomeSettings): MetronomePreset {
        val preset = MetronomePreset(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Untitled" },
            settings = settings.normalized(),
            createdAtEpochMs = System.currentTimeMillis(),
        )
        persist(_presets.value + preset)
        return preset
    }

    fun delete(id: String) {
        persist(_presets.value.filterNot { it.id == id })
    }

    fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        persist(_presets.value.map { if (it.id == id) it.copy(name = trimmed) else it })
    }

    private fun persist(presets: List<MetronomePreset>) {
        _presets.value = presets
        prefs.edit().putString(KEY_PRESETS, PresetCodec.encodePresets(presets)).apply()
    }

    private fun starterPresets(): List<MetronomePreset> = PresetFactory.starterPresets()

    private companion object {
        const val PREFS = "thaalam_presets"
        const val KEY_PRESETS = "presets_v1"
        const val KEY_SEEDED = "seeded_v1"
    }
}
