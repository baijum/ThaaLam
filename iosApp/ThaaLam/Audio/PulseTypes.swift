import Foundation
import shared

/// Platform-neutral pulse dynamics used inside the realtime audio engine.
enum PulseKind: Int, CaseIterable {
    case accent
    case normal
    case ghost
    case mute

    init(_ kind: BeatKind) {
        switch kind {
        case .accent: self = .accent
        case .ghost: self = .ghost
        case .mute: self = .mute
        default: self = .normal
        }
    }

    var displayName: String {
        switch self {
        case .accent: return "Accent"
        case .normal: return "Normal"
        case .ghost: return "Ghost"
        case .mute: return "Mute"
        }
    }

    var velocity: Float {
        switch self {
        case .accent: return 1.0
        case .normal: return 0.72
        case .ghost: return 0.34
        case .mute: return 0
        }
    }

    var isAudible: Bool { self != .mute }
}

/// Local mirror of the shared tala gesture so the render thread never touches
/// the Kotlin runtime.
enum PulseGesture: Int, CaseIterable {
    case none
    case clap
    case finger
    case wave
    case tali
    case khali

    init(_ gesture: AngaGesture) {
        switch gesture {
        case .clap: self = .clap
        case .finger: self = .finger
        case .wave: self = .wave
        case .tali: self = .tali
        case .khali: self = .khali
        default: self = .none
        }
    }

    var displayName: String {
        switch self {
        case .none: return "None"
        case .clap: return "Clap"
        case .finger: return "Finger count"
        case .wave: return "Wave"
        case .tali: return "Tali"
        case .khali: return "Khali"
        }
    }

    /// Pitch multiplier applied to the chosen click timbre.
    var pitch: Double {
        switch self {
        case .none: return 1.0
        case .clap: return 1.0
        case .finger: return 1.22
        case .wave: return 0.68
        case .tali: return 0.92
        case .khali: return 0.78
        }
    }

    /// Decay multiplier applied to the chosen click timbre.
    var decay: Double {
        switch self {
        case .none: return 1.0
        case .clap: return 0.95
        case .finger: return 0.65
        case .wave: return 1.30
        case .tali: return 1.05
        case .khali: return 1.10
        }
    }

    /// Noise added to the chosen click timbre.
    var noise: Double {
        switch self {
        case .none: return 0
        case .clap: return 0.10
        case .finger: return -0.04
        case .wave: return 0.18
        case .tali: return 0.08
        case .khali: return 0.04
        }
    }

    /// Velocity multiplier applied to the chosen click timbre.
    var gain: Float {
        switch self {
        case .none: return 1.0
        case .clap: return 1.0
        case .finger: return 0.82
        case .wave: return 0.78
        case .tali: return 0.96
        case .khali: return 0.64
        }
    }
}

/// Local mirror of the shared sound set so the render thread never touches the
/// Kotlin runtime.
enum ClickSound: String, CaseIterable, Identifiable {
    case click
    case woodblock
    case beep
    case bell
    case cowbell

    var id: String { rawValue }

    init(_ sound: SoundSet) {
        switch sound {
        case .woodblock: self = .woodblock
        case .beep: self = .beep
        case .bell: self = .bell
        case .cowbell: self = .cowbell
        default: self = .click
        }
    }

    var displayName: String {
        switch self {
        case .click: return "Click"
        case .woodblock: return "Woodblock"
        case .beep: return "Beep"
        case .bell: return "Bell"
        case .cowbell: return "Cowbell"
        }
    }

    var shared: SoundSet {
        switch self {
        case .click: return .click
        case .woodblock: return .woodblock
        case .beep: return .beep
        case .bell: return .bell
        case .cowbell: return .cowbell
        }
    }
}
