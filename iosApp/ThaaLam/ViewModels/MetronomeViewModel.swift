import AVFoundation
import Combine
import Foundation
import SwiftUI
import shared

/// Observable façade over the audio engine, shared settings and local storage.
///
/// All mutation goes through the shared Kotlin validators (`SettingsMutations`)
/// so iOS and Android cannot drift apart in what a setting means.
@MainActor
final class MetronomeViewModel: ObservableObject {
    @Published private(set) var settings: MetronomeSettings
    @Published private(set) var presets: [MetronomePreset]
    @Published private(set) var playback = PlaybackSnapshot()
    @Published private(set) var visualPlayback = PlaybackSnapshot()
    @Published private(set) var tapState: TapTempoResult?
    @Published private(set) var uiPreferences: UiPreferences
    @Published private(set) var currentTala: Tala?
    @Published var errorMessage: String?

    private let engine = MetronomeAudioEngine()
    private let tapDetector = TapTempoFactory.shared.create()
    private let haptic = UIImpactFeedbackGenerator(style: .medium)
    private var pollTimer: Timer?
    private var lastTickID: UInt64 = 0
    private var visualGeneration = 0
    private var interruptionObserver: NSObjectProtocol?

    init() {
        let loaded = Storage.loadSettings()
        settings = loaded
        presets = Storage.loadOrSeedPresets()
        uiPreferences = Storage.loadPreferences()
        currentTala = TalaLibrary.shared.find(id: loaded.talaId)
        engine.update(settings: loaded)
        startPolling()
        observeInterruptions()
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-layers"), settings.layers.isEmpty {
            addPolyrhythmLayer(pulses: 5, cycleBeats: 4)
        }
        if ProcessInfo.processInfo.arguments.contains("-autoplay") {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { [weak self] in
                self?.startPlayback()
            }
        }
        #endif
    }

    deinit {
        pollTimer?.invalidate()
        if let observer = interruptionObserver {
            NotificationCenter.default.removeObserver(observer)
        }
    }

    // MARK: - Transport

    func togglePlayback() {
        if playback.isPlaying {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    func startPlayback() {
        visualGeneration += 1
        lastTickID = 0
        let started = engine.start(settings: settings)
        if !started {
            errorMessage = "Could not start the audio engine."
            playback = .idle
            visualPlayback = .idle
            return
        }
        playback = PlaybackSnapshot(
            isPlaying: true,
            effectiveBpm: settings.effectiveBpm,
            baseBpm: settings.bpm
        )
        visualPlayback = playback
        haptic.prepare()
    }

    func stopPlayback() {
        engine.stop()
        visualGeneration += 1
        playback = .idle
        visualPlayback = .idle
    }

    private func pollEngine() {
        guard playback.isPlaying else { return }
        let tick = engine.latestTick()
        guard tick.id != 0, tick.id != lastTickID else { return }
        lastTickID = tick.id
        let snapshot = PlaybackSnapshot(
            isPlaying: true,
            bar: tick.bar,
            beat: tick.beat,
            sub: tick.sub,
            kind: tick.kind,
            isCountIn: tick.isCountIn,
            countInRemaining: tick.countInRemaining,
            effectiveBpm: tick.bpm,
            gesture: tick.gesture,
            baseBpm: settings.bpm
        )
        playback = snapshot

        let latency = uiPreferences.visualLatencyMs
        if latency <= 0 {
            visualPlayback = snapshot
        } else {
            let generation = visualGeneration
            DispatchQueue.main.asyncAfter(deadline: .now() + Double(latency) / 1000.0) { [weak self] in
                guard let self, generation == self.visualGeneration, self.playback.isPlaying else { return }
                self.visualPlayback = snapshot
            }
        }

        if tick.isMain && tick.audible && uiPreferences.hapticsEnabled {
            haptic.impactOccurred(intensity: hapticIntensity(for: tick.kind))
        }
    }

    private func startPolling() {
        let timer = Timer(timeInterval: 1.0 / 60.0, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.pollEngine()
            }
        }
        RunLoop.main.add(timer, forMode: .common)
        pollTimer = timer
    }

    private func observeInterruptions() {
        interruptionObserver = NotificationCenter.default.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: nil,
            queue: .main
        ) { [weak self] notification in
            guard
                let value = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                let type = AVAudioSession.InterruptionType(rawValue: value)
            else { return }
            if type == .began {
                Task { @MainActor in
                    self?.stopPlayback()
                }
            }
        }
    }

    private func hapticIntensity(for kind: PulseKind) -> CGFloat {
        switch kind {
        case .accent: return 1.0
        case .normal: return 0.65
        case .ghost: return 0.3
        case .mute: return 0
        }
    }

    // MARK: - Commit

    private func commit(_ updated: MetronomeSettings) {
        let normalized = updated.normalized()
        settings = normalized
        Storage.saveSettings(normalized)
        engine.update(settings: normalized)
        currentTala = TalaLibrary.shared.find(id: normalized.talaId)
    }

    // MARK: - Tempo

    func setBpm(_ bpm: Double) {
        commit(SettingsMutations.shared.setBpm(settings: settings, bpm: bpm))
    }

    func nudgeBpm(_ delta: Double) {
        setBpm(settings.bpm + delta)
    }

    func halfTempo() {
        setBpm(settings.bpm / 2.0)
    }

    func doubleTempo() {
        setBpm(settings.bpm * 2.0)
    }

    func tapTempo() {
        let now = Int64(Date().timeIntervalSince1970 * 1000.0)
        let result = tapDetector.tap(nowMs: now)
        tapState = result
        if let bpm = result.bpm?.doubleValue {
            setBpm((bpm * 10.0).rounded() / 10.0)
        }
        haptic.impactOccurred(intensity: 0.4)
    }

    func resetTapTempo() {
        tapDetector.reset()
        tapState = nil
    }

    // MARK: - Meter and groove

    func setMeter(beats: Int, unit: Int, grouping: String = "") {
        commit(
            SettingsMutations.shared.setMeter(
                settings: settings,
                beatsPerBar: Int32(beats),
                beatUnit: Int32(unit),
                grouping: grouping
            )
        )
    }

    func setSubdivision(ticks: Int) {
        commit(SettingsMutations.shared.setSubdivisionTicks(settings: settings, ticks: Int32(ticks)))
    }

    func setSwing(_ percent: Double) {
        commit(SettingsMutations.shared.setSwing(settings: settings, percent: percent))
    }

    func cycleBeat(_ index: Int) {
        commit(SettingsMutations.shared.cycleAccent(settings: settings, index: Int32(index)))
    }

    func applyGroupAccents() {
        commit(SettingsMutations.shared.setGroupAccents(settings: settings))
    }

    // MARK: - Practice

    func setCountInBars(_ bars: Int) {
        commit(SettingsMutations.shared.setCountInBars(settings: settings, bars: Int32(bars)))
    }

    func setGap(playBars: Int, silentBars: Int) {
        commit(
            SettingsMutations.shared.setGapTrainer(
                settings: settings,
                playBars: Int32(playBars),
                silentBars: Int32(silentBars)
            )
        )
    }

    func setMuteProbability(_ probability: Double) {
        commit(
            SettingsMutations.shared.setMuteProbability(
                settings: settings,
                probability: probability
            )
        )
    }

    func setProtectFirstBeat(_ protect: Bool) {
        commit(
            SettingsMutations.shared.setProtectFirstBeat(settings: settings, protect: protect)
        )
    }

    func setRamp(enabled: Bool, targetBpm: Double, stepBpm: Double, everyBars: Int) {
        commit(
            SettingsMutations.shared.setRamp(
                settings: settings,
                enabled: enabled,
                targetBpm: targetBpm,
                stepBpm: stepBpm,
                everyBars: Int32(everyBars)
            )
        )
    }

    // MARK: - Sound

    func setSound(_ sound: ClickSound) {
        commit(SettingsMutations.shared.setSoundSet(settings: settings, soundSet: sound.shared))
        preview(sound)
    }

    func setVolume(_ volume: Double) {
        commit(SettingsMutations.shared.setVolume(settings: settings, volume: volume))
    }

    func preview(_ sound: ClickSound? = nil) {
        engine.preview(sound: sound ?? ClickSound(settings.soundSet), volume: settings.volume)
    }

    // MARK: - Layers

    var canAddLayer: Bool {
        settings.layers.count < 3
    }

    func addSubdivisionLayer(_ subdivision: Subdivision) {
        let layer = LayerFactory.shared.subdivision(settings: settings, subdivision: subdivision)
        commit(SettingsMutations.shared.addLayer(settings: settings, layer: layer))
    }

    func addPolyrhythmLayer(pulses: Int, cycleBeats: Double) {
        let layer = LayerFactory.shared.polyrhythm(
            settings: settings,
            pulses: Int32(pulses),
            cycleBeats: cycleBeats
        )
        commit(SettingsMutations.shared.addLayer(settings: settings, layer: layer))
    }

    func addPolymeterLayer(beats: Int, beatUnit: Int) {
        let layer = LayerFactory.shared.polymeter(
            settings: settings,
            beats: Int32(beats),
            beatUnit: Int32(beatUnit)
        )
        commit(SettingsMutations.shared.addLayer(settings: settings, layer: layer))
    }

    func setLayerEnabled(_ id: String, _ enabled: Bool) {
        commit(
            SettingsMutations.shared.setLayerEnabled(
                settings: settings,
                id: id,
                enabled: enabled
            )
        )
    }

    func setLayerSolo(_ id: String, _ solo: Bool) {
        commit(
            SettingsMutations.shared.setLayerSolo(
                settings: settings,
                id: id,
                solo: solo
            )
        )
    }

    func cycleLayerStepOffset(_ id: String, index: Int) {
        commit(
            SettingsMutations.shared.cycleLayerStepOffset(
                settings: settings,
                id: id,
                index: Int32(index)
            )
        )
    }

    func setLayerStepOffset(_ id: String, index: Int, offsetMs: Double) {
        commit(
            SettingsMutations.shared.setLayerStepOffset(
                settings: settings,
                id: id,
                index: Int32(index),
                offsetMs: offsetMs
            )
        )
    }

    func setLayerGain(_ id: String, _ gain: Double) {
        commit(
            SettingsMutations.shared.setLayerGain(
                settings: settings,
                id: id,
                gain: gain
            )
        )
    }

    func setLayerOffset(_ id: String, _ offsetMs: Double) {
        commit(
            SettingsMutations.shared.setLayerOffset(
                settings: settings,
                id: id,
                offsetMs: offsetMs
            )
        )
    }

    func setLayerSound(_ id: String, _ sound: ClickSound) {
        commit(
            SettingsMutations.shared.setLayerSoundSet(
                settings: settings,
                id: id,
                soundSet: sound.shared
            )
        )
    }

    func setLayerPulses(_ id: String, _ pulses: Int) {
        guard let layer = settings.layers.first(where: { $0.id == id }) else { return }
        let updated = LayerMutations.shared.setPulses(layer: layer, pulses: Int32(pulses))
        commit(SettingsMutations.shared.updateLayer(settings: settings, layer: updated))
    }

    func setLayerCycle(_ id: String, _ cycleBeats: Double) {
        guard let layer = settings.layers.first(where: { $0.id == id }) else { return }
        let updated = LayerMutations.shared.setCycleBeats(layer: layer, cycleBeats: cycleBeats)
        commit(SettingsMutations.shared.updateLayer(settings: settings, layer: updated))
    }

    func setLayerSwing(_ id: String, _ percent: Double) {
        guard let layer = settings.layers.first(where: { $0.id == id }) else { return }
        let updated = LayerMutations.shared.setSwing(layer: layer, percent: percent)
        commit(SettingsMutations.shared.updateLayer(settings: settings, layer: updated))
    }

    func cycleLayerAccent(_ id: String, index: Int) {
        commit(
            SettingsMutations.shared.cycleLayerAccent(
                settings: settings,
                id: id,
                index: Int32(index)
            )
        )
    }

    func removeLayer(_ id: String) {
        commit(SettingsMutations.shared.removeLayer(settings: settings, id: id))
    }

    func previewLayer(_ id: String) {
        guard let layer = settings.layers.first(where: { $0.id == id }) else { return }
        engine.preview(sound: ClickSound(layer.soundSet), volume: settings.volume)
    }

    // MARK: - Talas

    func applyTala(
        _ tala: Tala,
        nadaiTicks: Int,
        kalam: Int,
        kalai: Int,
        eduppuOffsetAksharas: Double,
        autoStart: Bool
    ) {
        let updated = TalaFactory.shared.applyTala(
            tala: tala,
            base: settings,
            nadaiTicks: Int32(nadaiTicks),
            baseBpm: settings.bpm,
            kalam: Int32(kalam),
            kalai: Int32(kalai),
            eduppuOffsetAksharas: eduppuOffsetAksharas
        )
        commit(updated)
        if autoStart {
            startPlayback()
        }
    }

    func setKalam(_ kalam: Int) {
        commit(SettingsMutations.shared.setKalam(settings: settings, kalam: Int32(kalam)))
    }

    func setKalai(_ kalai: Int) {
        commit(SettingsMutations.shared.setKalai(settings: settings, kalai: Int32(kalai)))
    }

    func setEduppu(_ aksharas: Double) {
        commit(
            SettingsMutations.shared.setEduppu(
                settings: settings,
                eduppuOffsetAksharas: aksharas
            )
        )
    }

    func setTalaPlayback(kalam: Int, kalai: Int, eduppuOffsetAksharas: Double) {
        commit(
            SettingsMutations.shared.setTalaPlayback(
                settings: settings,
                kalam: Int32(kalam),
                kalai: Int32(kalai),
                eduppuOffsetAksharas: eduppuOffsetAksharas
            )
        )
    }

    func clearTala() {
        commit(TalaApplication.shared.clear(settings: settings))
    }

    // MARK: - Presets

    func savePreset(name: String) {
        let createdAt = Int64(Date().timeIntervalSince1970 * 1000.0)
        let preset = PresetFactory.shared.create(
            id: UUID().uuidString,
            name: name,
            settings: settings,
            createdAtEpochMs: createdAt
        )
        presets.insert(preset, at: 0)
        Storage.savePresets(presets)
    }

    func applyPreset(_ preset: MetronomePreset) {
        commit(preset.settings)
    }

    func deletePreset(_ preset: MetronomePreset) {
        presets.removeAll { $0.id == preset.id }
        Storage.savePresets(presets)
    }

    func renamePreset(_ preset: MetronomePreset, name: String) {
        guard let index = presets.firstIndex(where: { $0.id == preset.id }) else { return }
        presets[index] = PresetFactory.shared.rename(preset: preset, name: name)
        Storage.savePresets(presets)
    }

    // MARK: - Preferences

    func setHapticsEnabled(_ enabled: Bool) {
        var updated = uiPreferences
        updated.hapticsEnabled = enabled
        uiPreferences = updated
        Storage.savePreferences(updated)
        if enabled { haptic.prepare() }
    }

    func setFlashEnabled(_ enabled: Bool) {
        var updated = uiPreferences
        updated.flashEnabled = enabled
        uiPreferences = updated
        Storage.savePreferences(updated)
    }

    func setKeepScreenOn(_ enabled: Bool) {
        var updated = uiPreferences
        updated.keepScreenOn = enabled
        uiPreferences = updated
        Storage.savePreferences(updated)
    }

    func setVisualLatency(_ milliseconds: Int) {
        var updated = uiPreferences
        updated.visualLatencyMs = min(max(milliseconds, 0), 300)
        uiPreferences = updated
        Storage.savePreferences(updated)
    }

    func resetSettings() {
        commit(SettingsMutations.shared.reset())
    }
}
