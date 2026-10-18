package com.baijum.thaalam.data

import kotlinx.serialization.Serializable

/** A named, saved metronome configuration. */
@Serializable
data class MetronomePreset(
    val id: String,
    val name: String,
    val settings: MetronomeSettings,
    val createdAtEpochMs: Long = 0L,
) {
    val bpmLabel: String
        get() = settings.bpm.compactBpm()

    val summary: String
        get() {
            val parts = mutableListOf(settings.meter.label, settings.subdivision.shortLabel)
            if (settings.swingPercent > 50.5) parts += "${settings.swingPercent.toInt()}% swing"
            val layerCount = settings.layers.count { it.enabled }
            if (layerCount > 0) parts += "$layerCount layer${if (layerCount == 1) "" else "s"}"
            settings.talaId?.let { parts += "Tala" }
            return parts.joinToString(" · ")
        }
}

/**
 * Compact BPM formatting shared by both platforms: whole numbers stay clean,
 * fractional values keep a single decimal.
 */
fun Double.compactBpm(): String {
    val rounded = kotlin.math.round(this * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
