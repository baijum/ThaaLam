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
