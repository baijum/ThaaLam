package com.baijum.thaalam.domain

import com.baijum.thaalam.data.MetronomePreset
import com.baijum.thaalam.data.MetronomeSettings
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * JSON persistence codec shared by Android SharedPreferences and iOS
 * UserDefaults. Keeping the format in the shared module guarantees both
 * platforms can read each other's presets and settings.
 */
object PresetCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val presetListSerializer = ListSerializer(MetronomePreset.serializer())

    fun encodePresets(presets: List<MetronomePreset>): String =
        json.encodeToString(presetListSerializer, presets)

    fun decodePresets(raw: String?): List<MetronomePreset> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(presetListSerializer, raw) }
            .getOrDefault(emptyList())
            .map { it.copy(settings = it.settings.normalized()) }
    }

    fun encodeSettings(settings: MetronomeSettings): String =
        json.encodeToString(MetronomeSettings.serializer(), settings.normalized())

    fun decodeSettings(raw: String?): MetronomeSettings {
        if (raw.isNullOrBlank()) return MetronomeSettings().normalized()
        return runCatching { json.decodeFromString(MetronomeSettings.serializer(), raw) }
            .getOrDefault(MetronomeSettings())
            .normalized()
    }
}
