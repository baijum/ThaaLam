package com.baijum.thaalam.domain

import com.baijum.thaalam.data.AngaGesture
import com.baijum.thaalam.data.BeatKind
import com.baijum.thaalam.data.LayerSettings
import com.baijum.thaalam.data.SoundSet

/**
 * One scheduled click inside a voice's cycle.
 *
 * [pulse] is the index used to resolve live accents: the beat number for the
 * primary voice, or the pulse number for a layer. The primary voice also keeps
 * [beat], [sub] and [isMainBeat] so the UI can keep showing the familiar pulse
 * map without knowing that other voices exist.
 */
data class VoiceStep(
    val index: Int,
    val pulse: Int,
    val offsetBeats: Double,
    val kind: BeatKind,
    val beat: Int = -1,
    val sub: Int = 0,
    val isMainBeat: Boolean = false,
    /** Per-step timing offset in milliseconds, including the layer offset. */
    val offsetMs: Double = 0.0,
    /** Tala hand gesture resolved from the live tala, or [AngaGesture.NONE]. */
    val gesture: AngaGesture = AngaGesture.NONE,
)

/**
 * Immutable, precompiled click grid for one voice.
 *
 * The engines only need this structure: an ordered step list with the gap to
 * the next step, plus the sound/gain/offset snapshot. No musical reasoning
 * happens in the render callback.
 */
data class VoicePattern(
    val id: String,
    val name: String,
    val isPrimary: Boolean,
    val steps: List<VoiceStep>,
    val gaps: List<Double>,
    val cycleBeats: Double,
    val pulseCount: Int,
    val accentPattern: List<BeatKind>,
    val soundSet: SoundSet,
    val gain: Float,
    val offsetMs: Double,
    val swingPercent: Double,
    /** Changes only when the grid itself changes, not for accent/gain edits. */
    val gridKey: Int,
) {
    val stepCount: Int get() = steps.size

    /**
     * Silence between the start of the cycle and the first click. Non-zero for
     * an eduppu that starts between two tala pulses.
     */
    val leadInBeats: Double get() = steps.firstOrNull()?.offsetBeats ?: 0.0

    /** Beat distance from [stepIndex] to the next step, wrapping the cycle. */
    fun gapBeatsAfter(stepIndex: Int): Double {
        if (steps.isEmpty() || gaps.isEmpty()) return cycleBeats
        return gaps.getOrElse(stepIndex.coerceIn(gaps.indices)) {
            val current = steps[stepIndex.coerceIn(steps.indices)]
            cycleBeats - current.offsetBeats + leadInBeats
        }
    }

    /**
     * Resolve the live dynamic for a step. The engine passes the latest accent
     * snapshot, so editing an accent never restarts the bar.
     */
    fun kindAt(stepIndex: Int, liveAccents: List<BeatKind>): BeatKind {
        val step = steps.getOrNull(stepIndex) ?: return BeatKind.NORMAL
        return liveAccents.getOrElse(step.pulse) { step.kind }
    }

    companion object {
        const val PRIMARY_ID: String = "primary"
        const val COUNT_IN_ID: String = "count-in"
    }
}
