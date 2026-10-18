import Foundation
import shared

/// Preferences that are not part of the audio configuration.
struct UiPreferences {
    var hapticsEnabled = true
    var flashEnabled = false
    var keepScreenOn = true
    var visualLatencyMs = 0
    var tapCalibrationMs = 0
}

/// Local-only persistence. Settings and presets are stored as the same JSON the
/// Android build writes, so a backup can move between platforms by hand.
enum Storage {
    private static let defaults = UserDefaults.standard

    private enum Key {
        static let settings = "thaalam.settings.v1"
        static let presets = "thaalam.presets.v1"
        static let seeded = "thaalam.presets.seeded.v1"
        static let haptics = "thaalam.haptics"
        static let flash = "thaalam.flash"
        static let keepScreenOn = "thaalam.keepScreenOn"
        static let visualLatency = "thaalam.visualLatencyMs"
        static let tapCalibration = "thaalam.tapCalibrationMs"
        static let practice = "thaalam.practice.v1"
    }

    static func loadSettings() -> MetronomeSettings {
        PresetCodec.shared.decodeSettings(raw: defaults.string(forKey: Key.settings))
    }

    static func saveSettings(_ settings: MetronomeSettings) {
        defaults.set(PresetCodec.shared.encodeSettings(settings: settings), forKey: Key.settings)
    }

    static func loadPresets() -> [MetronomePreset] {
        PresetCodec.shared.decodePresets(raw: defaults.string(forKey: Key.presets))
    }

    static func savePresets(_ presets: [MetronomePreset]) {
        defaults.set(PresetCodec.shared.encodePresets(presets: presets), forKey: Key.presets)
    }

    static func loadPractice() -> PracticeData {
        PracticeCodec.shared.decode(raw: defaults.string(forKey: Key.practice))
    }

    static func savePractice(_ data: PracticeData) {
        defaults.set(PracticeCodec.shared.encode(data: data), forKey: Key.practice)
    }

    static func loadOrSeedPresets() -> [MetronomePreset] {
        let stored = loadPresets()
        if !stored.isEmpty || defaults.bool(forKey: Key.seeded) {
            return stored
        }
        let starters = PresetFactory.shared.starterPresets()
        savePresets(starters)
        defaults.set(true, forKey: Key.seeded)
        return starters
    }

    static func loadPreferences() -> UiPreferences {
        var prefs = UiPreferences()
        if defaults.object(forKey: Key.haptics) != nil {
            prefs.hapticsEnabled = defaults.bool(forKey: Key.haptics)
        }
        prefs.flashEnabled = defaults.bool(forKey: Key.flash)
        if defaults.object(forKey: Key.keepScreenOn) != nil {
            prefs.keepScreenOn = defaults.bool(forKey: Key.keepScreenOn)
        }
        prefs.visualLatencyMs = min(max(defaults.integer(forKey: Key.visualLatency), 0), 300)
        prefs.tapCalibrationMs = min(max(defaults.integer(forKey: Key.tapCalibration), -100), 200)
        return prefs
    }

    static func savePreferences(_ prefs: UiPreferences) {
        defaults.set(prefs.hapticsEnabled, forKey: Key.haptics)
        defaults.set(prefs.flashEnabled, forKey: Key.flash)
        defaults.set(prefs.keepScreenOn, forKey: Key.keepScreenOn)
        defaults.set(prefs.visualLatencyMs, forKey: Key.visualLatency)
        defaults.set(prefs.tapCalibrationMs, forKey: Key.tapCalibration)
    }
}
