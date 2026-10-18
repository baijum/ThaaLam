package com.baijum.thaalam.data

import com.baijum.thaalam.domain.MetronomeStateLogic
import kotlinx.serialization.Serializable

/**
 * Everything that defines how ThaaLam sounds and moves through time.
 *
 * Settings are immutable and normalised before reaching the audio engine, which
 * lets the engine treat every snapshot as a safe, fully validated value.
 */
@Serializable
data class MetronomeSettings(
    val bpm: Double = 100.0,
    val meter: Meter = Meter(),
    val subdivision: Subdivision = Subdivision.QUARTER,
    val accentPattern: List<BeatKind> = listOf(
        BeatKind.ACCENT,
        BeatKind.NORMAL,
        BeatKind.NORMAL,
        BeatKind.NORMAL,
    ),
    /** 50 = straight, 66.7 = triplet shuffle, 75 = dotted-sixteenth feel. */
    val swingPercent: Double = 50.0,
    val soundSet: SoundSet = SoundSet.CLICK,
    /** Master click volume, 0..1. */
    val volume: Float = 0.8f,
    /** Number of count-in bars played before the practice pattern begins. */
    val countInBars: Int = 1,
    /** Deterministic gap trainer: number of audible bars. 0 disables gaps. */
    val gapPlayBars: Int = 0,
    /** Deterministic gap trainer: number of silent bars after [gapPlayBars]. */
    val gapSilentBars: Int = 0,
    /** Stochastic beat muting, 0..1. */
    val muteProbability: Double = 0.0,
    /** Protects the sam / downbeat from stochastic muting and gap drops. */
    val protectFirstBeat: Boolean = true,
    /** Automatic tempo ramp. */
    val rampEnabled: Boolean = false,
    val rampTargetBpm: Double = 140.0,
    val rampStepBpm: Double = 2.0,
    val rampEveryBars: Int = 4,
    /** Identifier of the tala this configuration came from, if any. */
    val talaId: String? = null,
) {
    val isGapTraining: Boolean get() = gapPlayBars > 0 && gapSilentBars > 0

    /** A structurally valid snapshot of these settings. */
    fun normalized(): MetronomeSettings {
        val safeMeter = meter.normalized()
        val safePattern = MetronomeStateLogic.normalizeAccentPattern(
            beats = safeMeter.beatsPerBar,
            pattern = accentPattern,
            grouping = safeMeter.grouping,
        )
        return copy(
            bpm = bpm.coerceIn(MIN_BPM, MAX_BPM),
            meter = safeMeter,
            subdivision = subdivision,
            accentPattern = safePattern,
            swingPercent = swingPercent.coerceIn(MIN_SWING, MAX_SWING),
            volume = volume.coerceIn(0f, 1f),
            countInBars = countInBars.coerceIn(0, 4),
            gapPlayBars = gapPlayBars.coerceIn(0, 64),
            gapSilentBars = gapSilentBars.coerceIn(0, 64),
            muteProbability = muteProbability.coerceIn(0.0, 1.0),
            rampTargetBpm = rampTargetBpm.coerceIn(MIN_BPM, MAX_BPM),
            rampStepBpm = rampStepBpm.coerceIn(0.1, 20.0),
            rampEveryBars = rampEveryBars.coerceIn(1, 64),
        )
    }

    /** Settings with the first pulse of every group accented. */
    fun withGroupAccents(): MetronomeSettings = copy(
        accentPattern = MetronomeStateLogic.groupAccentPattern(
            beats = meter.beatsPerBar,
            grouping = meter.grouping,
        ),
    )

    companion object {
        const val MIN_BPM: Double = 10.0
        const val MAX_BPM: Double = 400.0
        const val MIN_SWING: Double = 50.0
        const val MAX_SWING: Double = 75.0
        const val DEFAULT_BPM: Double = 100.0
    }
}
