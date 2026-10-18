package com.baijum.thaalam.domain

import com.baijum.thaalam.data.Meter
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.Subdivision
import com.baijum.thaalam.data.Tala
import com.baijum.thaalam.data.TalaPlayback

/**
 * Turns a tala selection into a metronome configuration.
 *
 * Each akshara becomes a pulse of the meter, the anga lengths become the
 * additive grouping, and the tala's sam/tali/khali map becomes the accent
 * pattern. Kalam, kalai and eduppu are stored on [MetronomeSettings.talaPlayback]
 * instead of being folded into the tempo or meter, so the base tempo survives a
 * kalam change. The result is a regular [MetronomeSettings] snapshot, so the
 * audio engine needs no special tala mode at all.
 */
object TalaApplication {
    fun apply(
        tala: Tala,
        base: MetronomeSettings,
        nadai: Subdivision = Subdivision.QUARTER,
        bpm: Double? = null,
        playback: TalaPlayback = TalaPlayback(),
    ): MetronomeSettings = base.copy(
        bpm = bpm ?: base.bpm,
        meter = Meter(tala.aksharas, 4, tala.grouping),
        subdivision = nadai,
        accentPattern = tala.beatKinds,
        swingPercent = 50.0,
        talaId = tala.id,
        talaPlayback = playback,
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

    /**
     * Swift-friendly overload with the complete tala playback options. Kotlin
     * default arguments do not bridge to Swift, so the values are explicit here.
     */
    fun applyTalaWithPlayback(
        tala: Tala,
        base: MetronomeSettings,
        nadaiTicks: Int,
        baseBpm: Double,
        kalam: Int,
        kalai: Int,
        eduppuOffsetAksharas: Double,
    ): MetronomeSettings = apply(
        tala = tala,
        base = base,
        nadai = Subdivision.fromTicks(nadaiTicks),
        bpm = baseBpm,
        playback = TalaPlayback(
            kalam = kalam,
            kalai = kalai,
            eduppuOffsetAksharas = eduppuOffsetAksharas,
        ),
    )

    /** Replaces the playback options without touching tempo, meter or accents. */
    fun withPlayback(
        settings: MetronomeSettings,
        playback: TalaPlayback,
    ): MetronomeSettings = if (settings.talaId == null) {
        settings
    } else {
        settings.copy(talaPlayback = playback).normalized()
    }

    fun withKalam(settings: MetronomeSettings, kalam: Int): MetronomeSettings =
        withPlayback(settings, settings.talaPlayback.copy(kalam = kalam))

    fun withKalai(settings: MetronomeSettings, kalai: Int): MetronomeSettings =
        withPlayback(settings, settings.talaPlayback.copy(kalai = kalai))

    fun withEduppu(
        settings: MetronomeSettings,
        eduppuOffsetAksharas: Double,
    ): MetronomeSettings = withPlayback(
        settings,
        settings.talaPlayback.copy(eduppuOffsetAksharas = eduppuOffsetAksharas),
    )

    /** Clears an active tala while preserving the tempo and practice options. */
    fun clear(settings: MetronomeSettings): MetronomeSettings =
        settings.copy(talaId = null, talaPlayback = TalaPlayback())
}
