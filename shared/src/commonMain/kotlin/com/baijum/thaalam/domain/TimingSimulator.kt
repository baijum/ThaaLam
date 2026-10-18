package com.baijum.thaalam.domain

import com.baijum.thaalam.data.BeatKind
import com.baijum.thaalam.data.MetronomeSettings
import kotlin.math.floor
import kotlin.math.max

/** One scheduled click produced by the deterministic simulation. */
data class SimulatedTick(
    val frame: Long,
    val voiceId: String,
    val bar: Long,
    val beat: Int,
    val pulse: Int,
    val kind: BeatKind,
    val audible: Boolean,
    val isCountIn: Boolean,
    val bpm: Double,
)

/**
 * Frame-level simulation of the shared scheduling rules.
 *
 * The platform engines intentionally contain their own small scheduler loops,
 * so this simulator is the common contract: tests and golden fixtures describe
 * exactly where every voice should click. It is deterministic and has no
 * platform or audio-device dependency.
 */
object TimingSimulator {
    private const val MAX_EVENTS = 500_000

    fun run(
        settings: MetronomeSettings,
        totalFrames: Long,
        sampleRate: Int = 48_000,
    ): List<SimulatedTick> {
        if (totalFrames <= 0L || sampleRate <= 0) return emptyList()
        val normalized = settings.normalized()
        val primary = LayerCompiler.compilePrimary(normalized)
        val countIn = LayerCompiler.compileCountIn(normalized)
        val layers = LayerCompiler.activeLayers(normalized)
            .map(LayerCompiler::compileLayer)

        var currentBpm = normalized.bpm
        var barIndex = 1
        var primaryPattern = if (normalized.countInBars > 0) countIn else primary
        var pendingBarAdvance = false
        val runtimes = ArrayList<Runtime>(1 + layers.size)
        runtimes += Runtime(
            pattern = primaryPattern,
            stepIndex = 0,
            nextFrame = 0.0,
            isPrimary = true,
        )
        layers.forEach { layer ->
            runtimes += Runtime(
                pattern = layer,
                stepIndex = 0,
                nextFrame = 0.0,
                isPrimary = false,
            )
        }

        val ticks = ArrayList<SimulatedTick>()
        val lcg = PracticeLogic.Lcg()
        var guard = 0
        while (guard++ < MAX_EVENTS) {
            var chosen = runtimes[0]
            var chosenFrame = chosen.nextFrame + stepOffsetFrames(chosen, sampleRate)
            for (index in 1 until runtimes.size) {
                val runtime = runtimes[index]
                val frame = runtime.nextFrame + stepOffsetFrames(runtime, sampleRate)
                if (frame < chosenFrame) {
                    chosen = runtime
                    chosenFrame = frame
                }
            }
            if (chosenFrame >= totalFrames.toDouble()) break

            if (pendingBarAdvance && chosen.isPrimary) {
                pendingBarAdvance = false
                barIndex++
                val nextIsCountIn = barIndex <= normalized.countInBars
                val nextMusicalBar = max(1, barIndex - normalized.countInBars)
                currentBpm = if (nextIsCountIn) {
                    normalized.bpm
                } else {
                    PracticeLogic.bpmForBar(normalized, nextMusicalBar, false)
                }
                primaryPattern = if (nextIsCountIn) countIn else primary
                chosen.pattern = primaryPattern
                chosen.stepIndex = 0
            }

            val isCountIn = barIndex <= normalized.countInBars
            val musicalBar = max(1, barIndex - normalized.countInBars)
            val step = chosen.pattern.steps.getOrNull(chosen.stepIndex) ?: break
            val liveKind = if (chosen.isPrimary) {
                if (step.isMainBeat) {
                    normalized.accentPattern.getOrElse(step.beat) { step.kind }
                } else {
                    step.kind
                }
            } else {
                chosen.pattern.accentPattern.getOrElse(step.pulse) { step.kind }
            }
            val gapSilent = !isCountIn && PracticeLogic.isGapSilentBar(
                musicalBar,
                normalized.gapPlayBars,
                normalized.gapSilentBars,
            )
            val muted = if (chosen.isPrimary) {
                PracticeLogic.shouldMuteTick(
                    random = lcg.nextDouble(),
                    probability = normalized.muteProbability,
                    isFirstBeatOfBar = step.beat == 0 && step.sub == 0,
                    protectFirstBeat = normalized.protectFirstBeat,
                    isGapSilent = gapSilent,
                )
            } else {
                gapSilent
            }
            val audible = liveKind.isAudible && !muted && (!isCountIn || chosen.isPrimary)
            ticks += SimulatedTick(
                frame = max(0L, floor(chosenFrame).toLong()),
                voiceId = chosen.pattern.id,
                bar = if (isCountIn) 0L else musicalBar.toLong(),
                beat = step.beat,
                pulse = step.pulse,
                kind = liveKind,
                audible = audible,
                isCountIn = isCountIn,
                bpm = currentBpm,
            )

            val gapBeats = chosen.pattern.gapBeatsAfter(chosen.stepIndex)
            chosen.stepIndex++
            chosen.nextFrame += gapBeats * framesPerBeat(currentBpm, sampleRate)
            if (chosen.stepIndex >= chosen.pattern.stepCount) {
                chosen.stepIndex = 0
                if (chosen.isPrimary) {
                    // Advance the bar only when the next primary tick is due.
                    // Layers in between still belong to the old bar (important
                    // for count-in and gap boundaries).
                    pendingBarAdvance = true
                }
            }
        }
        return ticks
    }

    private fun framesPerBeat(bpm: Double, sampleRate: Int): Double =
        TempoMath.framesPerBeat(bpm, sampleRate)

    private fun stepOffsetFrames(runtime: Runtime, sampleRate: Int): Double {
        val step = runtime.pattern.steps.getOrNull(runtime.stepIndex) ?: return 0.0
        return step.offsetMs * sampleRate / 1000.0
    }

    private data class Runtime(
        var pattern: VoicePattern,
        var stepIndex: Int,
        var nextFrame: Double,
        val isPrimary: Boolean,
    )
}
