package com.baijum.thaalam.data

import com.baijum.thaalam.domain.PracticeLogic
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
    /**
     * Additional voices mixed over the primary pulse. Empty means the original
     * single-voice metronome. See [LayerSettings].
     */
    val layers: List<LayerSettings> = emptyList(),
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
    /**
     * Gap 2.0 custom bar pattern. `true` is a play bar, `false` a silent bar.
     * Empty means the legacy [gapPlayBars]/[gapSilentBars] cycle.
     */
    val gapPattern: List<Boolean> = emptyList(),
    /** Fade the click over the last N play bars before a gap. 0 is a hard cut. */
    val gapFadeBars: Int = 0,
    /** Hard constraint on consecutive silent bars in [gapPattern]. */
    val gapMaxSilentBars: Int = 4,
    /** Keep a soft sam click on silent bars so the player can re-enter. */
    val gapKeepSam: Boolean = false,
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
    /**
     * Kalam, kalai and eduppu for the active tala. Inert when [talaId] is null,
     * so leaving tala mode always returns to a plain metronome.
     */
    val talaPlayback: TalaPlayback = TalaPlayback(),
) {
    val isGapTraining: Boolean
        get() = gapPattern.isNotEmpty() || (gapPlayBars > 0 && gapSilentBars > 0)

    /**
     * The akshara rate actually sent to the audio engine: the base tempo scaled
     * by kalam. The base [bpm] is never overwritten, so switching kalam back to
     * 1x restores the tempo the user typed.
     */
    val effectiveBpm: Double
        get() = (bpm * talaPlayback.kalamFactor).coerceIn(MIN_BPM, MAX_EFFECTIVE_BPM)

    /** Clicks per akshara: nadai/subdivision multiplied by kalai. */
    val effectiveTicksPerBeat: Int
        get() = (subdivision.ticksPerBeat * talaPlayback.kalai)
            .coerceIn(1, MAX_EFFECTIVE_TICKS)

    /** True when a tala is active and the playback options are doing something. */
    val hasTalaPlayback: Boolean
        get() = talaId != null && !talaPlayback.isDefault

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
            layers = LayerSettings.normalizedList(layers),
            talaPlayback = if (talaId == null) TalaPlayback() else talaPlayback.normalized(),
            swingPercent = swingPercent.coerceIn(MIN_SWING, MAX_SWING),
            volume = volume.coerceIn(0f, 1f),
            countInBars = countInBars.coerceIn(0, 4),
            gapPlayBars = gapPlayBars.coerceIn(0, 64),
            gapSilentBars = gapSilentBars.coerceIn(0, 64),
            gapPattern = PracticeLogic.normalizeGapPattern(gapPattern, gapMaxSilentBars),
            gapFadeBars = gapFadeBars.coerceIn(0, 8),
            gapMaxSilentBars = gapMaxSilentBars.coerceIn(1, 16),
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

        /** Highest effective tempo with kalam 4x, i.e. 4 × [MAX_BPM]. */
        const val MAX_EFFECTIVE_BPM: Double = 1_600.0

        /** Highest supported clicks per akshara (nadai 9 × 2-kalai). */
        const val MAX_EFFECTIVE_TICKS: Int = 18
        const val MIN_SWING: Double = 50.0
        const val MAX_SWING: Double = 75.0
        const val DEFAULT_BPM: Double = 100.0
    }
}
