package com.baijum.thaalam.audio

import com.baijum.thaalam.data.BeatKind
import com.baijum.thaalam.data.SoundSet
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Additive click synthesiser used by the real-time audio thread.
 *
 * Every voice is generated procedurally, so the app ships no click samples and
 * can shift pitch/decay with the accent level. Voices survive buffer boundaries
 * (a bell tail is ~400 ms, far longer than one 256-frame buffer), and [render]
 * is allocation-free once the voice pool exists.
 */
class ClickSynth(private val sampleRate: Int) {

    private data class VoiceSpec(
        val frequency: Double,
        val harmonic: Double,
        val decayMs: Double,
        val noise: Double,
        val square: Boolean,
    )

    private class Voice {
        var remaining = 0
        var phase = 0.0
        var phase2 = 0.0
        var increment = 0.0
        var increment2 = 0.0
        var envelope = 0.0
        var envelopeDecay = 0.0
        /** Frames to skip before this voice becomes audible. */
        var delay = 0
        var gain = 0.0
        var noiseAmount = 0.0
        var square = false
        var rng = 0x2545F491L

        fun start(spec: VoiceSpec, gain: Float, sampleRate: Int, startFrame: Int) {
            remaining = ((spec.decayMs / 1000.0) * sampleRate * 1.15).toInt().coerceAtLeast(1)
            delay = startFrame.coerceAtLeast(0)
            phase = 0.0
            phase2 = 0.0
            increment = 2.0 * PI * spec.frequency / sampleRate
            increment2 = 2.0 * PI * (spec.frequency * spec.harmonic) / sampleRate
            envelope = 1.0
            // Exponential decay to roughly -50 dB over the nominal decay time.
            val decaySeconds = (spec.decayMs / 1000.0).coerceAtLeast(0.005)
            envelopeDecay = exp(-5.0 / (decaySeconds * sampleRate))
            this.gain = gain.toDouble()
            noiseAmount = spec.noise
            square = spec.square
            rng = 0x2545F491L
        }

        fun render(out: FloatArray, frames: Int) {
            var start = 0
            if (delay > 0) {
                val skip = minOf(delay, frames)
                delay -= skip
                start = skip
            }
            val count = minOf(frames - start, remaining)
            if (count <= 0) return
            var i = start
            val end = start + count
            while (i < end) {
                val tone = if (square) {
                    if (sin(phase) >= 0.0) 1.0 else -1.0
                } else {
                    sin(phase)
                }
                val harmonic = sin(phase2) * 0.45
                val noise = if (noiseAmount > 0.0) {
                    rng = rng xor (rng shl 13)
                    rng = rng xor (rng ushr 7)
                    rng = rng xor (rng shl 17)
                    ((rng and 0xFFFF).toDouble() / 32768.0 - 1.0) * noiseAmount
                } else {
                    0.0
                }
                out[i] += ((tone + harmonic + noise) * envelope * gain * 0.55).toFloat()

                phase += increment
                if (phase > 2.0 * PI) phase -= 2.0 * PI
                phase2 += increment2
                if (phase2 > 2.0 * PI) phase2 -= 2.0 * PI
                envelope *= envelopeDecay
                i++
            }
            remaining -= count
        }
    }

    private val voices = Array(VOICE_COUNT) { Voice() }

    /**
     * Starts a click. [kind] controls pitch and velocity; [isSubdivision] makes
     * subdivision clicks quieter and brighter so the underlying pulse stays
     * clearly audible.
     */
    fun trigger(
        soundSet: SoundSet,
        kind: BeatKind,
        isSubdivision: Boolean,
        volume: Float,
        startFrame: Int = 0,
    ) {
        if (kind == BeatKind.MUTE) return
        val spec = specFor(soundSet)
        val accentFactor = when (kind) {
            BeatKind.ACCENT -> 1.28
            BeatKind.NORMAL -> 1.0
            BeatKind.GHOST -> 0.9
            BeatKind.MUTE -> return
        }
        val subdivisionFactor = if (isSubdivision) 1.42 else 1.0
        val pitch = accentFactor * subdivisionFactor
        val velocity = kind.velocity * if (isSubdivision) 0.46f else 1.0f
        val gain = (velocity * volume.coerceIn(0f, 1f)).coerceIn(0f, 1f)

        var voice: Voice? = null
        var index = 0
        while (index < voices.size) {
            val candidate = voices[index]
            if (candidate.remaining <= 0) {
                voice = candidate
                break
            }
            index++
        }
        if (voice == null) return // all voices busy; drop rather than glitch
        voice.start(
            spec.copy(
                frequency = spec.frequency * pitch,
                decayMs = spec.decayMs * if (isSubdivision) 0.7 else 1.0,
            ),
            gain = gain,
            sampleRate = sampleRate,
            startFrame = startFrame,
        )
    }

    /** Renders all active voices into [out], clearing it first. */
    fun render(out: FloatArray, frames: Int) {
        java.util.Arrays.fill(out, 0, frames, 0f)
        for (voice in voices) {
            if (voice.remaining > 0) voice.render(out, frames)
        }
        for (i in 0 until frames) {
            out[i] = out[i].coerceIn(-1f, 1f)
        }
    }

    fun reset() {
        for (voice in voices) {
            voice.remaining = 0
            voice.delay = 0
        }
    }

    /** Generates one short preview buffer for the settings screen. */
    fun previewBuffer(soundSet: SoundSet, frames: Int): FloatArray {
        reset()
        trigger(soundSet, BeatKind.ACCENT, isSubdivision = false, volume = 0.9f)
        val buffer = FloatArray(frames)
        render(buffer, frames)
        return buffer
    }

    private fun specFor(soundSet: SoundSet): VoiceSpec = when (soundSet) {
        SoundSet.CLICK -> VoiceSpec(1580.0, 1.82, 32.0, 0.10, false)
        SoundSet.WOODBLOCK -> VoiceSpec(860.0, 1.48, 58.0, 0.34, false)
        SoundSet.BEEP -> VoiceSpec(1000.0, 2.0, 75.0, 0.02, true)
        SoundSet.BELL -> VoiceSpec(1760.0, 1.5, 420.0, 0.02, false)
        SoundSet.COWBELL -> VoiceSpec(540.0, 1.5, 150.0, 0.05, true)
    }

    private companion object {
        const val VOICE_COUNT = 12
    }
}
