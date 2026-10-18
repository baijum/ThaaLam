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

private struct EnginePattern {
    struct Step {
        let beat: Int
        let sub: Int
        let offset: Double
        let kind: PulseKind
        let isMain: Bool
    }

    let steps: [Step]
    let gaps: [Double]
    let lengthBeats: Double
    let key: Int

    static let empty = EnginePattern(steps: [], gaps: [], lengthBeats: 4, key: 0)

    static func make(pattern: RhythmPattern, key: Int) -> EnginePattern {
        let source = pattern.steps
        var steps: [Step] = []
        steps.reserveCapacity(source.count)
        for step in source {
            steps.append(
                Step(
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
            let next = index + 1 < steps.count ? steps[index + 1].offset : pattern.lengthBeats
            gaps.append(max(0, next - step.offset))
        }
        return EnginePattern(steps: steps, gaps: gaps, lengthBeats: pattern.lengthBeats, key: key)
    }
}

private struct EngineConfig {
    let pattern: EnginePattern
    let countInPattern: EnginePattern
    let accentPattern: [PulseKind]
    let sound: ClickSound
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
    let patternKey: Int

    static let empty = EngineConfig(
        pattern: .empty,
        countInPattern: .empty,
        accentPattern: [],
        sound: .click,
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
        rampEveryBars: 4,
        patternKey: 0
    )

    static func make(settings: MetronomeSettings) -> EngineConfig {
        let key = Int(PatternFactory.shared.patternKey(settings: settings))
        let pattern = EnginePattern.make(
            pattern: PatternFactory.shared.build(settings: settings),
            key: key
        )
        let countIn = EnginePattern.make(
            pattern: PatternFactory.shared.buildCountIn(settings: settings),
            key: key
        )
        return EngineConfig(
            pattern: pattern,
            countInPattern: countIn,
            accentPattern: settings.accentPattern.map { PulseKind($0) },
            sound: ClickSound(settings.soundSet),
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
            rampEveryBars: Int(settings.rampEveryBars),
            patternKey: key
        )
    }
}

/// Mutable render state. Only touched by the audio thread while the engine runs
/// (and by `reset` on the main thread immediately before it starts).
private final class RenderState {
    let configLock = OSAllocatedUnfairLock<EngineConfig>(initialState: .empty)
    let tickLock = OSAllocatedUnfairLock<EngineTickSnapshot>(initialState: .idle)
    let synth = ClickSynth()

    private(set) var framePosition = 0.0
    private var nextTickFrame = 0.0
    private var stepIndex = 0
    private var barIndex = 1
    private var currentBpm = 100.0
    private var lastBaseBpm = 100.0
    private var lastPatternKey = 0
    private var tickID: UInt64 = 0
    private var rng: UInt64 = 0x9E37_79B9_7F4A_7C15

    init(config: EngineConfig) {
        configLock.withLockUnchecked { $0 = config }
        reset(config: config)
    }

    func reset(config: EngineConfig) {
        framePosition = 0
        nextTickFrame = 0
        stepIndex = 0
        barIndex = 1
        currentBpm = config.bpm
        lastBaseBpm = config.bpm
        lastPatternKey = config.patternKey
        tickID = 0
        rng = 0x9E37_79B9_7F4A_7C15
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
        if config.pattern.steps.isEmpty {
            for i in 0..<frames { data[i] = 0 }
            return noErr
        }

        if config.patternKey != lastPatternKey {
            lastPatternKey = config.patternKey
            stepIndex = 0
            nextTickFrame = framePosition
        }
        if config.bpm != lastBaseBpm {
            lastBaseBpm = config.bpm
            currentBpm = config.bpm
        }

        let windowEnd = framePosition + Double(frames)
        var guardCount = 0
        while nextTickFrame < windowEnd && guardCount < 64 {
            guardCount += 1
            let offset = max(0, min(frames - 1, Int(nextTickFrame - framePosition)))
            let isCountIn = barIndex <= config.countInBars
            let musicalBar = max(1, barIndex - config.countInBars)
            let pattern = isCountIn ? config.countInPattern : config.pattern
            guard !pattern.steps.isEmpty else { break }
            let step = pattern.steps[min(stepIndex, pattern.steps.count - 1)]
            let liveKind: PulseKind = {
                if step.isMain, step.beat >= 0, step.beat < config.accentPattern.count {
                    return config.accentPattern[step.beat]
                }
                return step.kind
            }()

            let gapSilent = !isCountIn && Self.isGapSilent(
                bar: musicalBar,
                playBars: config.gapPlayBars,
                silentBars: config.gapSilentBars
            )
            let muted = Self.shouldMute(
                random: nextRandom(),
                probability: config.muteProbability,
                isFirstBeat: step.beat == 0 && step.sub == 0,
                protectFirstBeat: config.protectFirstBeat,
                isGapSilent: gapSilent
            )
            let audible = liveKind.isAudible && !muted
            if audible {
                synth.trigger(
                    sound: config.sound,
                    kind: liveKind,
                    isSubdivision: !step.isMain,
                    volume: config.volume,
                    startFrame: offset
                )
            }

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

            let gapBeats = stepIndex < pattern.gaps.count ? pattern.gaps[stepIndex] : 1.0
            stepIndex += 1
            if stepIndex >= pattern.steps.count {
                stepIndex = 0
                barIndex += 1
                let nextIsCountIn = barIndex <= config.countInBars
                let nextMusicalBar = max(1, barIndex - config.countInBars)
                currentBpm = nextIsCountIn
                    ? config.bpm
                    : Self.bpmForBar(config: config, bar: nextMusicalBar)
            }
            nextTickFrame += gapBeats * (60.0 / max(10.0, currentBpm)) * MetronomeAudioEngine.sampleRate
        }

        synth.render(into: data, frames: frames)
        framePosition += Double(frames)
        return noErr
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

/// Sample-accurate metronome engine for iOS.
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
        let config = EngineConfig.make(settings: settings)
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
        let config = EngineConfig.make(settings: settings)
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
