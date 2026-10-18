package com.baijum.thaalam.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import com.baijum.thaalam.data.BeatKind
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.Subdivision
import com.baijum.thaalam.domain.PracticeLogic
import com.baijum.thaalam.domain.RhythmPattern
import com.baijum.thaalam.domain.TempoMath
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
)

private data class EngineConfig(
    val settings: MetronomeSettings,
    val pattern: RhythmPattern,
    val countInPattern: RhythmPattern,
    val patternKey: Int,
)

/**
 * Sample-accurate metronome audio engine.
 *
 * Timing is derived exclusively from the number of frames written to an
 * [AudioTrack], never from a wall-clock timer. A dedicated high-priority thread
 * fills 256-frame buffers; click positions are computed as absolute frame
 * numbers with a double-precision phase accumulator, so fractional BPM values
 * (and swing/tuplet grids) never accumulate drift.
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

    /** Called from the audio thread on every scheduled tick. Keep it short. */
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
            var nextTickFrame = 0.0
            var stepIndex = 0
            var barIndex = 1
            var lastConfig = config
            var activePattern = patternForBar(lastConfig, barIndex)
            var currentBpm = lastConfig?.settings?.bpm ?: 100.0
            var lastBaseBpm = currentBpm
            var lastPatternKey = lastConfig?.patternKey ?: 0

            while (running) {
                val cfg = config ?: break
                if (cfg !== lastConfig) {
                    if (cfg.patternKey != lastPatternKey) {
                        // Meter/subdivision/swing changed: restart the grid from
                        // this buffer so the new pattern begins cleanly.
                        lastPatternKey = cfg.patternKey
                        stepIndex = 0
                        nextTickFrame = framePosition.toDouble()
                    }
                    if (cfg.settings.bpm != lastBaseBpm) {
                        currentBpm = cfg.settings.bpm
                        lastBaseBpm = cfg.settings.bpm
                    }
                    activePattern = patternForBar(cfg, barIndex)
                    lastConfig = cfg
                }

                // Schedule every tick that falls inside this buffer before
                // rendering, so a click at frame 100 is mixed at frame 100.
                val windowEnd = framePosition + FRAMES_PER_BUFFER
                var guard = 0
                while (nextTickFrame < windowEnd && guard < MAX_TICKS_PER_BUFFER) {
                    guard++
                    val offset = (nextTickFrame - framePosition).toInt().coerceIn(0, FRAMES_PER_BUFFER - 1)
                    val isCountIn = barIndex <= cfg.settings.countInBars
                    val musicalBar = (barIndex - cfg.settings.countInBars).coerceAtLeast(1)
                    val step = activePattern.steps.getOrNull(stepIndex)
                        ?: activePattern.steps.firstOrNull()
                        ?: break

                    val liveKind = if (step.isMainBeat) {
                        cfg.settings.accentPattern.getOrElse(step.beat) { step.kind }
                    } else {
                        step.kind
                    }
                    val gapSilent = !isCountIn && PracticeLogic.isGapSilentBar(
                        musicalBar,
                        cfg.settings.gapPlayBars,
                        cfg.settings.gapSilentBars,
                    )
                    val muted = PracticeLogic.shouldMuteTick(
                        random = lcg.nextDouble(),
                        probability = cfg.settings.muteProbability,
                        isFirstBeatOfBar = step.beat == 0 && step.sub == 0,
                        protectFirstBeat = cfg.settings.protectFirstBeat,
                        isGapSilent = gapSilent,
                    )
                    val audible = liveKind.isAudible && !muted
                    if (audible) {
                        synth.trigger(
                            soundSet = cfg.settings.soundSet,
                            kind = liveKind,
                            isSubdivision = !step.isMainBeat,
                            volume = cfg.settings.volume,
                            startFrame = offset,
                        )
                    }
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
                            countInBarsRemaining = (cfg.settings.countInBars - barIndex + 1).coerceAtLeast(0),
                        ),
                    )

                    val gapBeats = activePattern.gapBeatsAfter(stepIndex)
                    stepIndex++
                    if (stepIndex >= activePattern.stepCount) {
                        stepIndex = 0
                        barIndex++
                        activePattern = patternForBar(cfg, barIndex)
                        val nextIsCountIn = barIndex <= cfg.settings.countInBars
                        val nextMusicalBar = (barIndex - cfg.settings.countInBars).coerceAtLeast(1)
                        currentBpm = if (nextIsCountIn) {
                            cfg.settings.bpm
                        } else {
                            PracticeLogic.bpmForBar(cfg.settings, nextMusicalBar, false)
                        }
                    }
                    val framesPerBeat = TempoMath.framesPerBeat(currentBpm, SAMPLE_RATE)
                    nextTickFrame += gapBeats * framesPerBeat
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

    private fun patternForBar(cfg: EngineConfig?, barIndex: Int): RhythmPattern {
        val safe = cfg ?: return RhythmPattern.simple()
        return if (barIndex <= safe.settings.countInBars) safe.countInPattern else safe.pattern
    }

    private fun buildConfig(settings: MetronomeSettings): EngineConfig {
        val normalized = settings.normalized()
        val pattern = RhythmPattern.build(normalized)
        val countIn = RhythmPattern.build(
            normalized.copy(
                subdivision = Subdivision.QUARTER,
                swingPercent = 50.0,
                accentPattern = normalized.accentPattern,
            ),
        )
        val patternKey = listOf(
            normalized.meter.beatsPerBar,
            normalized.meter.beatUnit,
            normalized.meter.grouping,
            normalized.subdivision.ticksPerBeat,
            normalized.swingPercent,
        ).hashCode()
        return EngineConfig(
            settings = normalized,
            pattern = pattern,
            countInPattern = countIn,
            patternKey = patternKey,
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
        private const val MAX_TICKS_PER_BUFFER = 64

        /** Audio stream type hint kept for older platform integrations. */
        const val STREAM_TYPE = AudioManager.STREAM_MUSIC
    }
}
