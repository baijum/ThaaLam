import AVFoundation
import Foundation
import os
import shared

/// Snapshot of the transport for SwiftUI.
struct PlaybackSnapshot: Equatable {
    var isPlaying = false
    var bar = 0
    var beat = -1
    var sub = 0
    var kind: PulseKind = .normal
    var isCountIn = false
    var countInRemaining = 0
    var effectiveBpm = 0.0

    static let idle = PlaybackSnapshot()
}

/// The latest scheduled tick, published by the render thread for the UI timer.
struct EngineTickSnapshot {
    var id: UInt64 = 0
    var bar = 0
    var beat = -1
    var sub = 0
    var kind: PulseKind = .normal
    var isMain = false
    var audible = false
    var isCountIn = false
    var countInRemaining = 0
    var bpm = 0.0

    static let idle = EngineTickSnapshot()
}

/// Plain Swift mirror of one compiled voice. Built on the main thread from the
/// shared Kotlin compiler; the render callback only reads these values.
private struct EngineVoice {
    struct Step {
        let pulse: Int
        let beat: Int
        let sub: Int
        let offset: Double
        let kind: PulseKind
        let isMain: Bool
    }

    let id: String
    let steps: [Step]
    let gaps: [Double]
    let accents: [PulseKind]
    let sound: ClickSound
    let gain: Float
    let offsetFrames: Double
    let gridKey: Int
    let isPrimary: Bool

    static let empty = EngineVoice(
        id: "empty",
        steps: [],
        gaps: [],
        accents: [],
        sound: .click,
        gain: 1,
        offsetFrames: 0,
        gridKey: 0,
        isPrimary: true
    )

    static func make(voice: VoicePattern) -> EngineVoice {
        var steps: [Step] = []
        steps.reserveCapacity(voice.steps.count)
        for step in voice.steps {
            steps.append(
                Step(
                    pulse: Int(step.pulse),
                    beat: Int(step.beat),
                    sub: Int(step.sub),
                    offset: step.offsetBeats,
                    kind: PulseKind(step.kind),
                    isMain: step.isMainBeat
                )
            )
        }
        var gaps: [Double] = []
        gaps.reserveCapacity(steps.count)
        for (index, step) in steps.enumerated() {
            let next = index + 1 < steps.count ? steps[index + 1].offset : voice.cycleBeats
            gaps.append(max(0, next - step.offset))
        }
        return EngineVoice(
            id: voice.id,
            steps: steps,
            gaps: gaps,
            accents: voice.accentPattern.map { PulseKind($0) },
            sound: ClickSound(voice.soundSet),
            gain: voice.gain,
            offsetFrames: voice.offsetMs * MetronomeAudioEngine.sampleRate / 1000.0,
            gridKey: Int(voice.gridKey),
            isPrimary: voice.isPrimary
        )
    }
}

private struct EngineConfig {
    let revision: UInt64
    let primary: EngineVoice
    let countIn: EngineVoice
    let layers: [EngineVoice]
    let volume: Float
    let bpm: Double
    let countInBars: Int
    let gapPlayBars: Int
    let gapSilentBars: Int
    let muteProbability: Double
    let protectFirstBeat: Bool
    let rampEnabled: Bool
    let rampTargetBpm: Double
    let rampStepBpm: Double
    let rampEveryBars: Int

    static let empty = EngineConfig(
        revision: 0,
        primary: .empty,
        countIn: .empty,
        layers: [],
        volume: 0.8,
        bpm: 100,
        countInBars: 0,
        gapPlayBars: 0,
        gapSilentBars: 0,
        muteProbability: 0,
        protectFirstBeat: true,
        rampEnabled: false,
        rampTargetBpm: 140,
        rampStepBpm: 2,
        rampEveryBars: 4
    )

    static func make(settings: MetronomeSettings, revision: UInt64) -> EngineConfig {
        let voices = PatternFactory.shared.voices(settings: settings)
        guard let primary = voices.first else { return .empty }
        return EngineConfig(
            revision: revision,
            primary: EngineVoice.make(voice: primary),
            countIn: EngineVoice.make(voice: PatternFactory.shared.countInVoice(settings: settings)),
            layers: voices.dropFirst().map { EngineVoice.make(voice: $0) },
            volume: settings.volume,
            bpm: settings.bpm,
            countInBars: Int(settings.countInBars),
            gapPlayBars: Int(settings.gapPlayBars),
            gapSilentBars: Int(settings.gapSilentBars),
            muteProbability: settings.muteProbability,
            protectFirstBeat: settings.protectFirstBeat,
            rampEnabled: settings.rampEnabled,
            rampTargetBpm: settings.rampTargetBpm,
            rampStepBpm: settings.rampStepBpm,
            rampEveryBars: Int(settings.rampEveryBars)
        )
    }
}

private final class VoiceRuntime {
    var pattern: EngineVoice
    var stepIndex = 0
    var nextTickFrame = 0.0
    let isPrimary: Bool
    var frameOffset: Double

    init(pattern: EngineVoice, isPrimary: Bool, frameOffset: Double) {
        self.pattern = pattern
        self.isPrimary = isPrimary
        self.frameOffset = frameOffset
    }
}

/// Mutable render state. Only touched by the audio thread while the engine runs
/// (and by `reset` on the main thread immediately before it starts).
private final class RenderState {
    private static let maxVoices = 4

    let configLock = OSAllocatedUnfairLock<EngineConfig>(initialState: .empty)
    let tickLock = OSAllocatedUnfairLock<EngineTickSnapshot>(initialState: .idle)
    let synth = ClickSynth()

    private(set) var framePosition = 0.0
    private var runtimes: [VoiceRuntime?] = []
    private var barIndex = 1
    private var currentBpm = 100.0
    private var lastBaseBpm = 100.0
    private var lastRevision: UInt64 = 0
    private var lastCountInBars = 0
    private var pendingBarAdvance = false
    private var tickID: UInt64 = 0
    private var rng: UInt64 = 0x9E37_79B9_7F4A_7C15

    init(config: EngineConfig) {
        configLock.withLockUnchecked { $0 = config }
        reset(config: config)
    }

    func reset(config: EngineConfig) {
        framePosition = 0
        barIndex = 1
        currentBpm = config.bpm
        lastBaseBpm = config.bpm
        lastRevision = config.revision
        lastCountInBars = config.countInBars
        pendingBarAdvance = false
        tickID = 0
        rng = 0x9E37_79B9_7F4A_7C15
        runtimes = makeRuntimes(config: config)
        synth.reset()
        tickLock.withLockUnchecked { $0 = .idle }
    }

    func resetTick() {
        tickLock.withLockUnchecked { $0 = .idle }
    }

    func render(frames: Int, audioBufferList: UnsafeMutablePointer<AudioBufferList>) -> OSStatus {
        let buffers = UnsafeMutableAudioBufferListPointer(audioBufferList)
        guard let first = buffers.first,
              let data = first.mData?.assumingMemoryBound(to: Float.self) else {
            return noErr
        }

        let config = configLock.withLockUnchecked { $0 }
        if config.primary.steps.isEmpty {
            for i in 0..<frames { data[i] = 0 }
            return noErr
        }

        if config.revision != lastRevision {
            if applyConfig(config, framePosition: framePosition) {
                pendingBarAdvance = false
            }
            lastRevision = config.revision
        }
        if config.bpm != lastBaseBpm {
            lastBaseBpm = config.bpm
            currentBpm = config.bpm
        }

        let windowEnd = framePosition + Double(frames)
        var guardCount = 0
        while guardCount < 64 {
            guardCount += 1
            var chosen: VoiceRuntime?
            var chosenFrame = Double.greatestFiniteMagnitude
            for runtime in runtimes {
                guard let runtime else { continue }
                let frame = runtime.nextTickFrame + runtime.frameOffset
                if chosen == nil || frame < chosenFrame {
                    chosen = runtime
                    chosenFrame = frame
                }
            }
            guard let runtime = chosen, chosenFrame < windowEnd else { break }

            if pendingBarAdvance && runtime.isPrimary {
                pendingBarAdvance = false
                barIndex += 1
                let nextIsCountIn = barIndex <= config.countInBars
                let nextMusicalBar = max(1, barIndex - config.countInBars)
                currentBpm = nextIsCountIn
                    ? config.bpm
                    : Self.bpmForBar(config: config, bar: nextMusicalBar)
                runtime.pattern = nextIsCountIn ? config.countIn : config.primary
                runtime.stepIndex = 0
            }

            let offset = max(0, min(frames - 1, Int(chosenFrame - framePosition)))
            let isCountIn = barIndex <= config.countInBars
            let musicalBar = max(1, barIndex - config.countInBars)
            guard !runtime.pattern.steps.isEmpty else { break }
            let step = runtime.pattern.steps[min(runtime.stepIndex, runtime.pattern.steps.count - 1)]
            let liveKind: PulseKind = {
                if step.pulse >= 0, step.pulse < runtime.pattern.accents.count {
                    return runtime.pattern.accents[step.pulse]
                }
                return step.kind
            }()

            let gapSilent = !isCountIn && Self.isGapSilent(
                bar: musicalBar,
                playBars: config.gapPlayBars,
                silentBars: config.gapSilentBars
            )
            let muted: Bool = {
                if !runtime.isPrimary { return gapSilent }
                return Self.shouldMute(
                    random: nextRandom(),
                    probability: config.muteProbability,
                    isFirstBeat: step.beat == 0 && step.sub == 0,
                    protectFirstBeat: config.protectFirstBeat,
                    isGapSilent: gapSilent
                )
            }()
            let audible = liveKind.isAudible && !muted && (!isCountIn || runtime.isPrimary)
            if audible {
                synth.trigger(
                    sound: runtime.pattern.sound,
                    kind: liveKind,
                    isSubdivision: !step.isMain,
                    volume: config.volume * runtime.pattern.gain,
                    startFrame: offset
                )
            }

            if runtime.isPrimary {
                tickID &+= 1
                let snapshot = EngineTickSnapshot(
                    id: tickID,
                    bar: isCountIn ? 0 : musicalBar,
                    beat: step.beat,
                    sub: step.sub,
                    kind: liveKind,
                    isMain: step.isMain,
                    audible: audible,
                    isCountIn: isCountIn,
                    countInRemaining: max(0, config.countInBars - barIndex + 1),
                    bpm: currentBpm
                )
                tickLock.withLockUnchecked { $0 = snapshot }
            }

            let gapBeats = runtime.stepIndex < runtime.pattern.gaps.count
                ? runtime.pattern.gaps[runtime.stepIndex]
                : 1.0
            runtime.stepIndex += 1
            runtime.nextTickFrame += gapBeats
                * (60.0 / max(10.0, currentBpm))
                * MetronomeAudioEngine.sampleRate
            if runtime.stepIndex >= runtime.pattern.steps.count {
                runtime.stepIndex = 0
                if runtime.isPrimary {
                    // Advance the bar only when the next primary tick is due.
                    // A layer tick in between still belongs to the old bar.
                    pendingBarAdvance = true
                }
            }
        }

        synth.render(into: data, frames: frames)
        framePosition += Double(frames)
        return noErr
    }

    private func makeRuntimes(config: EngineConfig) -> [VoiceRuntime?] {
        var runtimes = [VoiceRuntime?](repeating: nil, count: Self.maxVoices)
        let primaryPattern = config.countInBars > 0 ? config.countIn : config.primary
        runtimes[0] = VoiceRuntime(pattern: primaryPattern, isPrimary: true, frameOffset: 0)
        for (index, layer) in config.layers.prefix(Self.maxVoices - 1).enumerated() {
            runtimes[index + 1] = VoiceRuntime(
                pattern: layer,
                isPrimary: false,
                frameOffset: layer.offsetFrames
            )
        }
        return runtimes
    }

    /// Applies a new config without stopping the clock. Returns true when the
    /// primary grid restarted and a pending bar advance must be discarded.
    private func applyConfig(_ config: EngineConfig, framePosition: Double) -> Bool {
        let currentPrimary = barIndex <= config.countInBars ? config.countIn : config.primary
        guard runtimes.count > 0, let primary = runtimes[0] else { return false }
        var primaryReset = false
        if primary.pattern.gridKey != currentPrimary.gridKey ||
            config.countInBars != lastCountInBars {
            primary.stepIndex = 0
            primary.nextTickFrame = framePosition
            primaryReset = true
        }
        primary.pattern = currentPrimary

        for index in 1..<Self.maxVoices {
            guard let runtime = runtimes[index] else { continue }
            if !config.layers.contains(where: { $0.id == runtime.pattern.id }) {
                runtimes[index] = nil
            }
        }
        for layer in config.layers.prefix(Self.maxVoices - 1) {
            var slot: Int?
            for index in 1..<Self.maxVoices {
                if runtimes[index]?.pattern.id == layer.id {
                    slot = index
                    break
                }
            }
            if slot == nil {
                for index in 1..<Self.maxVoices where runtimes[index] == nil {
                    slot = index
                    break
                }
            }
            guard let index = slot else { continue }
            if let runtime = runtimes[index] {
                if runtime.pattern.gridKey != layer.gridKey {
                    runtime.stepIndex = 0
                    runtime.nextTickFrame = framePosition
                }
                runtime.pattern = layer
                runtime.frameOffset = layer.offsetFrames
            } else {
                let runtime = VoiceRuntime(
                    pattern: layer,
                    isPrimary: false,
                    frameOffset: layer.offsetFrames
                )
                runtime.nextTickFrame = framePosition
                runtimes[index] = runtime
            }
        }
        lastCountInBars = config.countInBars
        return primaryReset
    }

    private func nextRandom() -> Double {
        rng = rng &* 6364136223846793005 &+ 1442695040888963407
        return Double((rng >> 33) & 0x7FFF_FFFF) / Double(0x8000_0000)
    }

    private static func isGapSilent(bar: Int, playBars: Int, silentBars: Int) -> Bool {
        guard playBars > 0, silentBars > 0, bar > 0 else { return false }
        let cycle = playBars + silentBars
        return (bar - 1) % cycle >= playBars
    }

    private static func shouldMute(
        random: Double,
        probability: Double,
        isFirstBeat: Bool,
        protectFirstBeat: Bool,
        isGapSilent: Bool
    ) -> Bool {
        if isGapSilent { return true }
        if isFirstBeat && protectFirstBeat { return false }
        return probability > 0 && random < probability
    }

    private static func bpmForBar(config: EngineConfig, bar: Int) -> Double {
        guard config.rampEnabled else { return config.bpm }
        let stepCount = max(0, (bar - 1) / max(1, config.rampEveryBars))
        var bpm = config.bpm
        for _ in 0..<stepCount {
            if bpm < config.rampTargetBpm {
                bpm = min(config.rampTargetBpm, bpm + max(0.1, config.rampStepBpm))
            } else if bpm > config.rampTargetBpm {
                bpm = max(config.rampTargetBpm, bpm - max(0.1, config.rampStepBpm))
            }
        }
        return bpm
    }
}

/// Sample-accurate multi-voice metronome engine for iOS.
///
/// Scheduling happens inside `AVAudioSourceNode`'s render callback and is
/// derived solely from rendered frame counts. The render path avoids the Kotlin
/// runtime: settings are converted into plain Swift values when they change.
final class MetronomeAudioEngine {
    static let sampleRate = 48_000.0

    private let engine = AVAudioEngine()
    private let format = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 1)!
    private let state = RenderState(config: .empty)
    private var sourceNode: AVAudioSourceNode?
    private(set) var isRunning = false
    private var revision: UInt64 = 0
    private var previewEngine: AVAudioEngine?
    private var previewPlayer: AVAudioPlayerNode?

    init() {
        configureSession(active: false)
        attachSourceNode()
    }

    private func attachSourceNode() {
        let node = AVAudioSourceNode(format: format) { [state] _, _, frameCount, audioBufferList -> OSStatus in
            state.render(frames: Int(frameCount), audioBufferList: audioBufferList)
        }
        engine.attach(node)
        engine.connect(node, to: engine.mainMixerNode, format: format)
        engine.mainMixerNode.outputVolume = 1.0
        sourceNode = node
    }

    @discardableResult
    func start(settings: MetronomeSettings) -> Bool {
        if isRunning {
            update(settings: settings)
            return true
        }
        revision &+= 1
        let config = EngineConfig.make(settings: settings, revision: revision)
        state.configLock.withLockUnchecked { $0 = config }
        state.reset(config: config)
        configureSession(active: true)
        engine.prepare()
        do {
            try engine.start()
            isRunning = true
            return true
        } catch {
            isRunning = false
            return false
        }
    }

    func update(settings: MetronomeSettings) {
        revision &+= 1
        let config = EngineConfig.make(settings: settings, revision: revision)
        state.configLock.withLockUnchecked { $0 = config }
    }

    func stop() {
        guard isRunning else { return }
        engine.stop()
        isRunning = false
        state.resetTick()
    }

    func latestTick() -> EngineTickSnapshot {
        state.tickLock.withLockUnchecked { $0 }
    }

    func preview(sound: ClickSound, volume: Float) {
        configureSession(active: true)
        let frames = 24_000
        guard let buffer = AVAudioPCMBuffer(
            pcmFormat: format,
            frameCapacity: AVAudioFrameCount(frames)
        ), let data = buffer.floatChannelData?[0] else { return }
        buffer.frameLength = AVAudioFrameCount(frames)
        let synth = ClickSynth()
        synth.trigger(sound: sound, kind: .accent, isSubdivision: false, volume: volume, startFrame: 0)
        synth.render(into: data, frames: frames)

        let preview = AVAudioEngine()
        let player = AVAudioPlayerNode()
        preview.attach(player)
        preview.connect(player, to: preview.mainMixerNode, format: format)
        preview.prepare()
        do {
            try preview.start()
            player.scheduleBuffer(buffer, at: nil, options: [], completionHandler: nil)
            player.play()
        } catch {
            return
        }
        previewEngine = preview
        previewPlayer = player
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) { [weak self] in
            preview.stop()
            self?.previewEngine = nil
            self?.previewPlayer = nil
        }
    }

    private func configureSession(active: Bool) {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default, options: [.mixWithOthers])
        if active {
            try? session.setActive(true)
        }
    }
}
