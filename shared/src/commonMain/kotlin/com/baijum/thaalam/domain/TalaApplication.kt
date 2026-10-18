package com.baijum.thaalam.domain

import com.baijum.thaalam.data.Meter
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.Subdivision
import com.baijum.thaalam.data.Tala

/**
 * Turns a tala selection into a metronome configuration.
 *
 * Each akshara becomes a pulse of the meter, the anga lengths become the
 * additive grouping, and the tala's sam/tali/khali map becomes the accent
 * pattern. The result is a regular [MetronomeSettings] snapshot, so the audio
 * engine needs no special tala mode at all.
 */
object TalaApplication {
    fun apply(
        tala: Tala,
        base: MetronomeSettings,
        nadai: Subdivision = Subdivision.QUARTER,
        bpm: Double? = null,
    ): MetronomeSettings = base.copy(
        bpm = bpm ?: base.bpm,
        meter = Meter(tala.aksharas, 4, tala.grouping),
        subdivision = nadai,
        accentPattern = tala.beatKinds,
        swingPercent = 50.0,
        talaId = tala.id,
    ).normalized()

    /** Swift-friendly overload that takes the nadai as a tick count. */
    fun applyTala(
        tala: Tala,
        base: MetronomeSettings,
        nadaiTicks: Int,
    ): MetronomeSettings = apply(
        tala = tala,
        base = base,
        nadai = Subdivision.fromTicks(nadaiTicks),
        bpm = null,
    )

    /** Clears an active tala while preserving the tempo and practice options. */
    fun clear(settings: MetronomeSettings): MetronomeSettings =
        settings.copy(talaId = null)
}
