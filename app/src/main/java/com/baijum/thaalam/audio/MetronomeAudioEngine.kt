package com.baijum.thaalam.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.baijum.thaalam.data.AngaGesture
import com.baijum.thaalam.data.BeatKind
import com.baijum.thaalam.data.LayerSettings
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.Subdivision
import com.baijum.thaalam.domain.LayerCompiler
import com.baijum.thaalam.domain.PracticeLogic
import com.baijum.thaalam.domain.TempoMath
import com.baijum.thaalam.domain.VoicePattern
import kotlin.math.max

/** A single scheduled tick, emitted from the audio thread for the UI/controller. */
data class EngineTick(
    val bar: Long,
    val beat: Int,
    val sub: Int,
    val kind: BeatKind,
    val isMainBeat: Boolean,
    val audible: Boolean,
    val isCountIn: Boolean,
    val bpm: Double,
    val countInBarsRemaining: Int,
    val gesture: AngaGesture,
    /** Host monotonic time when this tick was scheduled. */
    val hostTimeMs: Long,
)

private data class EngineConfig(
    val settings: MetronomeSettings,
    val primary: VoicePattern,
    val countIn: VoicePattern,
    val layers: List<VoicePattern>,
    /** Performance tempo: base BPM scaled by kalam. */
    val bpm: Double,
)

private class VoiceRuntime(
    var pattern: VoicePattern,
    var gridKey: Int,
    val isPrimary: Boolean,
    var stepIndex: Int = 0,
    var nextTickFrame: Double = 0.0,
) {
    val id: String get() = pattern.id
}

/**
 * Sample-accurate multi-voice metronome audio engine.
 *
 * Timing is derived exclusively from the number of frames written to an
 * [AudioTrack], never from a wall-clock timer. The primary voice and up to
 * three layers each keep their own step cursor, but share one master frame
 * position and one tempo. Clicks are mixed at their exact sample offset.
 *
 * The engine is deliberately a plain class rather than a service so it can be
 * owned by an application-scoped controller and survive Activity recreation.
 */
class MetronomeAudioEngine {

    @Volatile
    private var running = false

    @Volatile
    private var config: EngineConfig? = null

    private var thread: Thread? = null

    @Volatile
    private var activeTrack: AudioTrack? = null

    /** Called from the audio thread on every primary-voice tick. Keep it short. */
    @Volatile
    var onTick: ((EngineTick) -> Unit)? = null

    /** Called on a background thread if the audio device cannot start. */
    @Volatile
    var onError: ((String) -> Unit)? = null

    val isPlaying: Boolean get() = running

    fun start(settings: MetronomeSettings) {
        if (running) {
            update(settings)
            return
        }
        config = buildConfig(settings)
        running = true
        thread = Thread({ audioLoop() }, "ThaaLam-Audio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun update(settings: MetronomeSettings) {
        config = buildConfig(settings)
    }

    fun stop() {
        running = false
        thread?.let { worker ->
            worker.interrupt()
            if (Thread.currentThread() !== worker) {
                runCatching { worker.join(750) }
            }
        }
        thread = null
        // The audio loop releases the AudioTrack in its finally block. Do not
        // release it again here: a double release races with the writer thread.
    }

    /** Plays a one-shot preview of [settings]'s sound set on a detached track. */
    fun preview(settings: MetronomeSettings, kind: BeatKind = BeatKind.ACCENT) {
        Thread {
            var track: AudioTrack? = null
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val frames = (SAMPLE_RATE * 0.5f).toInt()
                val minBytes = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_FLOAT,
                )
                val bytes = max(minBytes, frames * Float.SIZE_BYTES)
                track = buildTrack(bytes)
                if (track.state != AudioTrack.STATE_INITIALIZED) return@Thread
                val synth = ClickSynth(SAMPLE_RATE)
                val buffer = FloatArray(frames)
                synth.trigger(settings.soundSet, kind, isSubdivision = false, volume = settings.volume)
                synth.render(buffer, frames)
                track.play()
                track.write(buffer, 0, frames, AudioTrack.WRITE_BLOCKING)
                Thread.sleep(560)
            } catch (t: Throwable) {
                Log.w(TAG, "Preview failed", t)
            } finally {
                runCatching { track?.stop() }
                runCatching { track?.release() }
            }
        }.apply {
            name = "ThaaLam-Preview"
            start()
        }
    }

    private fun audioLoop() {
        var track: AudioTrack? = null
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val minBytes = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
            )
            val bytes = max(minBytes, FRAMES_PER_BUFFER * Float.SIZE_BYTES * 4)
            track = buildTrack(bytes)
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                throw IllegalStateException("AudioTrack failed to initialise (state=${track.state})")
            }
            activeTrack = track
            track.play()

            val synth = ClickSynth(SAMPLE_RATE)
            val buffer = FloatArray(FRAMES_PER_BUFFER)
            val lcg = PracticeLogic.Lcg(seed = 0x9E3779B97F4A7C15uL.toLong())

            var framePosition = 0L
            var barIndex = 1
            var currentBpm = config?.bpm ?: 100.0
            var lastBaseBpm = currentBpm
            var lastConfig = config ?: return
            var pendingBarAdvance = false
            var pendingBoundaryFrame = 0.0
            var pendingNextBar = 1
            var pendingNextBpm = currentBpm
            var pendingNextIsCountIn = false
            val runtimes = createRuntimes(lastConfig)

            while (running) {
                val cfg = config ?: break
                if (cfg !== lastConfig) {
                    if (cfg.bpm != lastBaseBpm) {
                        currentBpm = cfg.bpm
                        lastBaseBpm = cfg.bpm
                    }
                    applyConfig(
                        cfg = cfg,
                        previous = lastConfig,
                        runtimes = runtimes,
                        framePosition = framePosition,
                        barIndex = barIndex,
                        currentBpm = currentBpm,
                    ).let { primaryReset ->
                        if (primaryReset) pendingBarAdvance = false
                    }
                    if (pendingBarAdvance) {
                        // Re-time the next bar with the new snapshot. This keeps
                        // a live kalam/BPM change exact even when it lands in the
                        // short window between the last step and the bar line.
                        val nextIsCountIn = pendingNextBar <= cfg.settings.countInBars
                        val nextMusicalBar = (pendingNextBar - cfg.settings.countInBars)
                            .coerceAtLeast(1)
                        val nextBpm = if (nextIsCountIn) {
                            cfg.bpm
                        } else {
                            PracticeLogic.bpmForBar(cfg.settings, nextMusicalBar, false)
                        }
                        val nextPattern = if (nextIsCountIn) cfg.countIn else cfg.primary
                        pendingNextBpm = nextBpm
                        pendingNextIsCountIn = nextIsCountIn
                        runtimes[0]?.nextTickFrame = pendingBoundaryFrame +
                            nextPattern.leadInBeats *
                            TempoMath.framesPerBeat(nextBpm, SAMPLE_RATE)
                    }
                    lastConfig = cfg
                }

                val windowEnd = framePosition + FRAMES_PER_BUFFER
                var guard = 0
                while (guard < MAX_TICKS_PER_BUFFER) {
                    guard++
                    var chosen: VoiceRuntime? = null
                    var chosenFrame = Double.MAX_VALUE
                    for (runtime in runtimes) {
                        if (runtime == null) continue
                        val stepOffsetMs = runtime.pattern.steps
                            .getOrNull(runtime.stepIndex)?.offsetMs ?: 0.0
                        val candidate = runtime.nextTickFrame +
                            stepOffsetMs * SAMPLE_RATE / 1000.0
                        if (chosen == null || candidate < chosenFrame) {
                            chosen = runtime
                            chosenFrame = candidate
                        }
                    }
                    if (chosen == null || chosenFrame >= windowEnd) break

                    if (pendingBarAdvance && chosen.isPrimary) {
                        pendingBarAdvance = false
                        barIndex = pendingNextBar
                        currentBpm = pendingNextBpm
                        // Resolve the pattern from the *current* snapshot so an
                        // accent or sound edit in the wrap window is not lost.
                        val nextPattern = if (pendingNextIsCountIn) cfg.countIn else cfg.primary
                        chosen.pattern = nextPattern
                        chosen.gridKey = nextPattern.gridKey
                        chosen.stepIndex = 0
                    }

                    val offset = (chosenFrame - framePosition)
                        .toInt()
                        .coerceIn(0, FRAMES_PER_BUFFER - 1)
                    val isCountIn = barIndex <= cfg.settings.countInBars
                    val musicalBar = (barIndex - cfg.settings.countInBars).coerceAtLeast(1)
                    val step = chosen.pattern.steps.getOrNull(chosen.stepIndex)
                        ?: chosen.pattern.steps.firstOrNull()
                        ?: break

                    val liveKind = chosen.pattern.accentPattern
                        .getOrElse(step.pulse) { step.kind }
                    val gapSilent = !isCountIn &&
                        PracticeLogic.isGapSilentBar(musicalBar, cfg.settings)
                    val gapGain = if (isCountIn) {
                        1f
                    } else {
                        PracticeLogic.gapGainForBar(musicalBar, cfg.settings)
                    }
                    val keepSam = gapSilent &&
                        cfg.settings.gapKeepSam &&
                        chosen.isPrimary &&
                        step.beat == 0 &&
                        step.sub == 0
                    val muted = if (chosen.isPrimary) {
                        if (keepSam) {
                            false
                        } else {
                            PracticeLogic.shouldMuteTick(
                                random = lcg.nextDouble(),
                                probability = cfg.settings.muteProbability,
                                isFirstBeatOfBar = step.beat == 0 && step.sub == 0,
                                protectFirstBeat = cfg.settings.protectFirstBeat,
                                isGapSilent = gapSilent,
                            )
                        }
                    } else {
                        gapSilent
                    }
                    val practiceGain = if (keepSam) PracticeLogic.GAP_SAM_GAIN else gapGain
                    val audible = liveKind.isAudible && !muted && (!isCountIn || chosen.isPrimary)
                    if (audible) {
                        synth.trigger(
                            soundSet = chosen.pattern.soundSet,
                            kind = liveKind,
                            isSubdivision = !step.isMainBeat,
                            volume = cfg.settings.volume * chosen.pattern.gain * practiceGain,
                            startFrame = offset,
                            gesture = step.gesture,
                        )
                    }
                    if (chosen.isPrimary) {
                        onTick?.invoke(
                            EngineTick(
                                bar = if (isCountIn) 0L else musicalBar.toLong(),
                                beat = step.beat,
                                sub = step.sub,
                                kind = liveKind,
                                isMainBeat = step.isMainBeat,
                                audible = audible,
                                isCountIn = isCountIn,
                                bpm = currentBpm,
                                countInBarsRemaining = (cfg.settings.countInBars - barIndex + 1)
                                    .coerceAtLeast(0),
                                gesture = step.gesture,
                                hostTimeMs = SystemClock.elapsedRealtime(),
                            ),
                        )
                    }

                    val gapBeats = chosen.pattern.gapBeatsAfter(chosen.stepIndex)
                    chosen.stepIndex++
                    chosen.nextTickFrame += gapBeats * TempoMath.framesPerBeat(currentBpm, SAMPLE_RATE)
                    if (chosen.stepIndex >= chosen.pattern.stepCount) {
                        chosen.stepIndex = 0
                        if (chosen.isPrimary) {
                            // The bar advances when the next primary tick is due,
                            // not now: a layer tick in between still belongs to
                            // the old bar (count-in/gap boundaries). The next
                            // pattern's own lead-in is applied here because an
                            // eduppu can give the count-in and musical bar
                            // different first-click offsets.
                            pendingBarAdvance = true
                            pendingBoundaryFrame = chosenFrame +
                                (chosen.pattern.cycleBeats - step.offsetBeats) *
                                TempoMath.framesPerBeat(currentBpm, SAMPLE_RATE)
                            pendingNextBar = barIndex + 1
                            val nextIsCountIn = pendingNextBar <= cfg.settings.countInBars
                            val nextMusicalBar = (pendingNextBar - cfg.settings.countInBars)
                                .coerceAtLeast(1)
                            pendingNextBpm = if (nextIsCountIn) {
                                cfg.bpm
                            } else {
                                PracticeLogic.bpmForBar(cfg.settings, nextMusicalBar, false)
                            }
                            val nextPattern = if (nextIsCountIn) cfg.countIn else cfg.primary
                            pendingNextIsCountIn = nextIsCountIn
                            chosen.nextTickFrame = pendingBoundaryFrame +
                                nextPattern.leadInBeats *
                                TempoMath.framesPerBeat(pendingNextBpm, SAMPLE_RATE)
                        }
                    }
                }

                synth.render(buffer, FRAMES_PER_BUFFER)
                var written = 0
                while (written < FRAMES_PER_BUFFER && running) {
                    val result = track.write(
                        buffer,
                        written,
                        FRAMES_PER_BUFFER - written,
                        AudioTrack.WRITE_BLOCKING,
                    )
                    if (result < 0) {
                        throw IllegalStateException("AudioTrack write failed: $result")
                    }
                    written += result
                }
                framePosition += FRAMES_PER_BUFFER
            }
        } catch (t: Throwable) {
            if (running) {
                Log.e(TAG, "Audio engine crashed", t)
                running = false
                onError?.invoke(t.message ?: "Audio engine stopped")
            }
        } finally {
            running = false
            runCatching { track?.stop() }
            runCatching { track?.flush() }
            runCatching { track?.release() }
            activeTrack = null
        }
    }

    private fun createRuntimes(cfg: EngineConfig): Array<VoiceRuntime?> {
        val runtimes = arrayOfNulls<VoiceRuntime>(MAX_VOICES)
        val primaryPattern = if (cfg.settings.countInBars > 0) cfg.countIn else cfg.primary
        runtimes[0] = VoiceRuntime(
            pattern = primaryPattern,
            gridKey = primaryPattern.gridKey,
            isPrimary = true,
        ).also {
            it.nextTickFrame = primaryPattern.leadInBeats *
                TempoMath.framesPerBeat(cfg.bpm, SAMPLE_RATE)
        }
        cfg.layers.take(LayerSettings.MAX_LAYERS).forEachIndexed { index, layer ->
            runtimes[index + 1] = VoiceRuntime(
                pattern = layer,
                gridKey = layer.gridKey,
                isPrimary = false,
            )
        }
        return runtimes
    }

    private fun applyConfig(
        cfg: EngineConfig,
        previous: EngineConfig,
        runtimes: Array<VoiceRuntime?>,
        framePosition: Long,
        barIndex: Int,
        currentBpm: Double,
    ): Boolean {
        val currentPrimary = if (barIndex <= cfg.settings.countInBars) cfg.countIn else cfg.primary
        val primary = runtimes[0] ?: return false
        var primaryReset = false
        val countInChanged = cfg.settings.countInBars != previous.settings.countInBars
        if (primary.gridKey != currentPrimary.gridKey || countInChanged) {
            primary.stepIndex = 0
            primary.nextTickFrame = framePosition.toDouble() +
                currentPrimary.leadInBeats * TempoMath.framesPerBeat(currentBpm, SAMPLE_RATE)
            primaryReset = true
        }
        primary.pattern = currentPrimary
        primary.gridKey = currentPrimary.gridKey

        for (index in 1 until MAX_VOICES) {
            val runtime = runtimes[index] ?: continue
            if (cfg.layers.none { it.id == runtime.id }) {
                runtimes[index] = null
            }
        }
        cfg.layers.take(LayerSettings.MAX_LAYERS).forEach { layer ->
            var slot = -1
            for (index in 1 until MAX_VOICES) {
                if (runtimes[index]?.id == layer.id) {
                    slot = index
                    break
                }
            }
            if (slot < 0) {
                for (index in 1 until MAX_VOICES) {
                    if (runtimes[index] == null) {
                        slot = index
                        break
                    }
                }
            }
            if (slot < 0) return@forEach
            val runtime = runtimes[slot]
            if (runtime == null) {
                runtimes[slot] = VoiceRuntime(
                    pattern = layer,
                    gridKey = layer.gridKey,
                    isPrimary = false,
                ).also { it.nextTickFrame = framePosition.toDouble() }
            } else {
                if (runtime.gridKey != layer.gridKey) {
                    runtime.stepIndex = 0
                    runtime.nextTickFrame = framePosition.toDouble()
                }
                runtime.pattern = layer
                runtime.gridKey = layer.gridKey
            }
        }
        return primaryReset
    }

    private fun buildConfig(settings: MetronomeSettings): EngineConfig {
        val normalized = settings.normalized()
        val voices = LayerCompiler.compile(normalized)
        return EngineConfig(
            settings = normalized,
            primary = voices.first(),
            countIn = LayerCompiler.compileCountIn(normalized),
            layers = voices.drop(1),
            bpm = normalized.effectiveBpm,
        )
    }

    private fun buildTrack(bufferBytes: Int): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setBufferSizeInBytes(bufferBytes)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        .build()

    companion object {
        private const val TAG = "ThaaLamAudio"
        const val SAMPLE_RATE = 48_000
        const val FRAMES_PER_BUFFER = 256
        private const val MAX_VOICES = 1 + LayerSettings.MAX_LAYERS
        private const val MAX_TICKS_PER_BUFFER = 64

        /** Audio stream type hint kept for older platform integrations. */
        const val STREAM_TYPE = AudioManager.STREAM_MUSIC
    }
}
