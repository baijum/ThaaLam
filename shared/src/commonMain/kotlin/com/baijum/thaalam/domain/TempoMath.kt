package com.baijum.thaalam.domain

import com.baijum.thaalam.data.MetronomeSettings
import kotlin.math.round

/** Pure tempo ↔ sample-frame mathematics shared by both audio engines. */
object TempoMath {
    const val MIN_BPM: Double = 10.0
    const val MAX_BPM: Double = 400.0

    fun clampBpm(bpm: Double): Double = bpm.coerceIn(MIN_BPM, MAX_BPM)

    fun secondsPerBeat(bpm: Double): Double = 60.0 / clampBpm(bpm)

    fun framesPerBeat(bpm: Double, sampleRate: Int): Double =
        secondsPerBeat(bpm) * sampleRate

    fun framesPerTick(bpm: Double, ticksPerBeat: Int, sampleRate: Int): Double =
        framesPerBeat(bpm, sampleRate) / ticksPerBeat.coerceAtLeast(1)

    /** The BPM after one bar of a stepped tempo ramp. */
    fun rampStep(currentBpm: Double, targetBpm: Double, stepBpm: Double): Double {
        val current = clampBpm(currentBpm)
        val target = clampBpm(targetBpm)
        if (stepBpm <= 0.0) return current
        val step = stepBpm.coerceAtLeast(0.1)
        return when {
            current < target -> (current + step).coerceAtMost(target)
            current > target -> (current - step).coerceAtLeast(target)
            else -> current
        }
    }

    /** Display label such as "120" or "120.5". */
    fun formatBpm(bpm: Double): String {
        val rounded = round(bpm * 10.0) / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }

    /** The traditional Italian tempo marking closest to a BPM value. */
    fun tempoMarking(bpm: Double): String = when {
        bpm < 20 -> "Larghissimo"
        bpm < 40 -> "Grave"
        bpm < 60 -> "Largo"
        bpm < 66 -> "Lento"
        bpm < 76 -> "Adagio"
        bpm < 108 -> "Andante"
        bpm < 120 -> "Moderato"
        bpm < 156 -> "Allegro"
        bpm < 176 -> "Vivace"
        bpm < 200 -> "Presto"
        else -> "Prestissimo"
    }

    /** Suggested tap-tempo bounds for a practice session. */
    fun isPracticeTempo(bpm: Double): Boolean = bpm in 40.0..240.0

    /** BPM after a user presses the half/double tempo buttons. */
    fun half(bpm: Double): Double = clampBpm(bpm / 2.0)

    fun double(bpm: Double): Double = clampBpm(bpm * 2.0)

    /** The approximate duration in seconds of [bars] bars at [bpm]. */
    fun barsDurationSeconds(bars: Int, bpm: Double, meterBeats: Int): Double =
        bars.coerceAtLeast(0) * meterBeats.coerceAtLeast(1) * secondsPerBeat(bpm)

    /** Effective BPM after applying a tempo ramp for [barIndex] bars (1-based). */
    fun rampedBpm(settings: MetronomeSettings, barIndex: Int): Double {
        if (!settings.rampEnabled) return clampBpm(settings.bpm)
        val steps = ((barIndex - 1).coerceAtLeast(0) / settings.rampEveryBars.coerceAtLeast(1))
        var bpm = clampBpm(settings.bpm)
        repeat(steps) {
            bpm = rampStep(bpm, settings.rampTargetBpm, settings.rampStepBpm)
        }
        return bpm
    }
}
