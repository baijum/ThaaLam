import Foundation

/// Additive click synthesiser for the realtime render callback.
///
/// Mirrors the Android implementation: twelve polyphonic voices, exponential
/// decay, a noise transient and optional square-wave timbres. Voices survive
/// buffer boundaries and support a sample offset so a click can begin at the
/// exact frame it is scheduled for.
final class ClickSynth {
    private struct VoiceSpec {
        let frequency: Double
        let harmonic: Double
        let decayMs: Double
        let noise: Double
        let square: Bool
    }

    private final class Voice {
        var remaining = 0
        var delay = 0
        var phase = 0.0
        var phase2 = 0.0
        var increment = 0.0
        var increment2 = 0.0
        var envelope = 0.0
        var envelopeDecay = 0.0
        var gain = 0.0
        var noiseAmount = 0.0
        var square = false
        var rng: UInt64 = 0x2545_F491

        func start(spec: VoiceSpec, gain: Float, sampleRate: Double, startFrame: Int) {
            remaining = max(1, Int((spec.decayMs / 1000.0) * sampleRate * 1.15))
            delay = max(0, startFrame)
            phase = 0
            phase2 = 0
            increment = 2.0 * Double.pi * spec.frequency / sampleRate
            increment2 = 2.0 * Double.pi * (spec.frequency * spec.harmonic) / sampleRate
            envelope = 1.0
            let decaySeconds = max(spec.decayMs / 1000.0, 0.005)
            envelopeDecay = exp(-5.0 / (decaySeconds * sampleRate))
            self.gain = Double(gain)
            noiseAmount = spec.noise
            square = spec.square
            rng = 0x2545_F491
        }

        func render(into out: UnsafeMutablePointer<Float>, frames: Int) {
            var start = 0
            if delay > 0 {
                let skip = min(delay, frames)
                delay -= skip
                start = skip
            }
            let count = min(frames - start, remaining)
            if count <= 0 { return }
            var i = start
            let end = start + count
            while i < end {
                let tone: Double
                if square {
                    tone = sin(phase) >= 0 ? 1.0 : -1.0
                } else {
                    tone = sin(phase)
                }
                let harmonic = sin(phase2) * 0.45
                var noise = 0.0
                if noiseAmount > 0 {
                    rng ^= (rng << 13)
                    rng ^= (rng >> 7)
                    rng ^= (rng << 17)
                    noise = (Double(rng & 0xFFFF) / 32768.0 - 1.0) * noiseAmount
                }
                out[i] += Float((tone + harmonic + noise) * envelope * gain * 0.55)

                phase += increment
                if phase > 2.0 * Double.pi { phase -= 2.0 * Double.pi }
                phase2 += increment2
                if phase2 > 2.0 * Double.pi { phase2 -= 2.0 * Double.pi }
                envelope *= envelopeDecay
                i += 1
            }
            remaining -= count
        }
    }

    private var voices: [Voice] = (0..<12).map { _ in Voice() }

    func trigger(
        sound: ClickSound,
        kind: PulseKind,
        isSubdivision: Bool,
        volume: Float,
        startFrame: Int
    ) {
        guard kind != .mute else { return }
        let base = spec(for: sound)
        let accentFactor: Double = {
            switch kind {
            case .accent: return 1.28
            case .normal: return 1.0
            case .ghost: return 0.9
            case .mute: return 1.0
            }
        }()
        let subdivisionFactor = isSubdivision ? 1.42 : 1.0
        let pitch = accentFactor * subdivisionFactor
        let velocity = kind.velocity * (isSubdivision ? 0.46 : 1.0)
        let gain = max(0, min(1, velocity * max(0, min(1, volume))))

        guard let voice = voices.first(where: { $0.remaining <= 0 }) else { return }
        voice.start(
            spec: VoiceSpec(
                frequency: base.frequency * pitch,
                harmonic: base.harmonic,
                decayMs: base.decayMs * (isSubdivision ? 0.7 : 1.0),
                noise: base.noise,
                square: base.square
            ),
            gain: gain,
            sampleRate: 48_000,
            startFrame: startFrame
        )
    }

    /// Clears [out] and mixes every active voice into it.
    func render(into out: UnsafeMutablePointer<Float>, frames: Int) {
        for i in 0..<frames {
            out[i] = 0
        }
        for voice in voices where voice.remaining > 0 {
            voice.render(into: out, frames: frames)
        }
        for i in 0..<frames {
            out[i] = min(max(out[i], -1), 1)
        }
    }

    func reset() {
        for voice in voices {
            voice.remaining = 0
            voice.delay = 0
        }
    }

    private func spec(for sound: ClickSound) -> VoiceSpec {
        switch sound {
        case .click:
            return VoiceSpec(frequency: 1580, harmonic: 1.82, decayMs: 32, noise: 0.10, square: false)
        case .woodblock:
            return VoiceSpec(frequency: 860, harmonic: 1.48, decayMs: 58, noise: 0.34, square: false)
        case .beep:
            return VoiceSpec(frequency: 1000, harmonic: 2.0, decayMs: 75, noise: 0.02, square: true)
        case .bell:
            return VoiceSpec(frequency: 1760, harmonic: 1.5, decayMs: 420, noise: 0.02, square: false)
        case .cowbell:
            return VoiceSpec(frequency: 540, harmonic: 1.5, decayMs: 150, noise: 0.05, square: true)
        }
    }
}
