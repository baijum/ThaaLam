import SwiftUI
import shared

/// Group-aware, tappable beat map. Mirrors the Compose implementation.
struct BeatGridView: View {
    let settings: MetronomeSettings
    let playback: PlaybackSnapshot
    var markers: [String?] = []
    var bols: [String] = []
    var gestures: [AngaGesture] = []
    var showLegend = true
    let onTap: (Int) -> Void

    private var groups: [[Int]] {
        let parsed = settings.meter.groupingLabel
            .split(separator: "+")
            .compactMap { Int($0.trimmingCharacters(in: .whitespaces)) }
            .filter { $0 > 0 }
        let beats = Int(settings.meter.beatsPerBar)
        let lengths = parsed.isEmpty ? [beats] : parsed
        var result: [[Int]] = []
        var index = 0
        for length in lengths {
            var group: [Int] = []
            for _ in 0..<length {
                if index >= beats { break }
                group.append(index)
                index += 1
            }
            if !group.isEmpty { result.append(group) }
        }
        while index < beats {
            result.append([index])
            index += 1
        }
        return result
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            FlowLayout(spacing: 8, lineSpacing: 8) {
                ForEach(Array(groups.enumerated()), id: \.offset) { entry in
                    HStack(spacing: 4) {
                        ForEach(entry.element, id: \.self) { index in
                            BeatPulseView(
                                index: index,
                                kind: kind(at: index),
                                active: playback.isPlaying && playback.beat == index,
                                marker: markers.indices.contains(index) ? markers[index] : nil,
                                bol: bols.indices.contains(index) ? bols[index] : nil,
                                gesture: gestures.indices.contains(index) ? gestures[index] : .none,
                                onTap: { onTap(index) }
                            )
                        }
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 8)
                    .background(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(ThaaLamTheme.surfaceVariant.opacity(0.45))
                    )
                }
            }
            if showLegend {
                BeatLegendView()
            }
        }
    }

    private func kind(at index: Int) -> PulseKind {
        guard settings.accentPattern.indices.contains(index) else { return .normal }
        return PulseKind(settings.accentPattern[index])
    }
}

struct BeatPulseView: View {
    let index: Int
    let kind: PulseKind
    let active: Bool
    let marker: String?
    let bol: String?
    let gesture: AngaGesture
    let onTap: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(spacing: 2) {
            Text(marker ?? " ")
                .font(.system(size: 10, weight: .bold))
                .foregroundStyle(color)
                .frame(height: 13)
            Button(action: onTap) {
                ZStack {
                    Circle()
                        .fill(active ? color : color.opacity(0.16))
                    Circle()
                        .strokeBorder(color, lineWidth: 1.5)
                    Text("\(index + 1)")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(active ? Color(hex: 0x0E1116) : ThaaLamTheme.textPrimary)
                    if kind == .mute {
                        Rectangle()
                            .fill(ThaaLamTheme.outline)
                            .frame(width: 1.5, height: 26)
                            .rotationEffect(.degrees(-45))
                    }
                }
                .frame(width: 36, height: 36)
                .scaleEffect(active ? 1.16 : 1)
                .animation(reduceMotion ? nil : .easeOut(duration: 0.09), value: active)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(accessibilityLabel)
            .accessibilityHint("Double tap to change accent")
            .accessibilityAddTraits(.isButton)
            Text(bol ?? (gesture == .none ? " " : gesture.shortLabel))
                .font(.system(size: 9, weight: active && bol != nil ? .bold : .regular))
                .foregroundStyle(
                    active && (bol != nil || gesture != .none)
                        ? color
                        : ThaaLamTheme.textSecondary
                )
                .frame(width: 42, height: 13)
                .lineLimit(1)
        }
    }

    private var color: Color {
        switch kind {
        case .accent: return ThaaLamTheme.saffron
        case .normal: return ThaaLamTheme.teal
        case .ghost: return ThaaLamTheme.lavender
        case .mute: return ThaaLamTheme.outline
        }
    }

    private var accessibilityLabel: String {
        var parts = ["Beat \(index + 1)", kind.displayName]
        if let bol, !bol.isEmpty { parts.append(bol) }
        if gesture != .none { parts.append(gesture.displayName) }
        if active { parts.append("current beat") }
        return parts.joined(separator: ", ")
    }
}

struct BeatLegendView: View {
    var body: some View {
        FlowLayout(spacing: 14, lineSpacing: 4) {
            legend("Accent", ThaaLamTheme.saffron)
            legend("Normal", ThaaLamTheme.teal)
            legend("Ghost", ThaaLamTheme.lavender)
            legend("Mute", ThaaLamTheme.outline)
        }
    }

    private func legend(_ label: String, _ color: Color) -> some View {
        HStack(spacing: 5) {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(label)
                .font(.system(size: 11))
                .foregroundStyle(ThaaLamTheme.textSecondary)
        }
    }
}

/// Read-only cycle diagram for the tala detail sheet.
struct TalaCycleView: View {
    let tala: Tala
    let activeBeat: Int
    let isPlaying: Bool

    private var groups: [(start: Int, length: Int)] {
        var result: [(Int, Int)] = []
        var index = 0
        for anga in tala.angas {
            result.append((index, Int(anga.length)))
            index += Int(anga.length)
        }
        return result
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            FlowLayout(spacing: 8, lineSpacing: 8) {
                ForEach(Array(groups.enumerated()), id: \.offset) { entry in
                    HStack(spacing: 4) {
                        ForEach(entry.element.start..<(entry.element.start + entry.element.length), id: \.self) { beat in
                            let kind = kind(at: beat)
                            let active = isPlaying && activeBeat == beat
                            let marker = tala.markerAt(index: Int32(beat))
                            let gesture = tala.gestureAt(index: Int32(beat))
                            let color = color(for: kind)
                            VStack(spacing: 2) {
                                Text(marker ?? (gesture == .none ? " " : gesture.symbol))
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundStyle(marker != nil ? color : ThaaLamTheme.textSecondary)
                                    .frame(height: 13)
                                ZStack {
                                    Circle().fill(active ? color : color.opacity(0.14))
                                    Circle().strokeBorder(color, lineWidth: 1)
                                    Text("\(beat + 1)")
                                        .font(.system(size: 11, weight: .bold))
                                        .foregroundStyle(active ? Color(hex: 0x0E1116) : ThaaLamTheme.textPrimary)
                                }
                                .frame(width: 30, height: 30)
                                Text(tala.bolAt(index: Int32(beat)) ?? " ")
                                    .font(.system(size: 9, weight: active ? .bold : .regular))
                                    .foregroundStyle(
                                        active ? color : ThaaLamTheme.textSecondary
                                    )
                                    .frame(width: 38, height: 12)
                                    .lineLimit(1)
                            }
                        }
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 8)
                    .background(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(ThaaLamTheme.surfaceVariant.opacity(0.45))
                    )
                }
            }
            Text("Gestures: ● clap · ○ finger · ◐ wave · X tali · 0 khali")
                .font(.system(size: 10))
                .foregroundStyle(ThaaLamTheme.textSecondary)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            "Tala cycle, \(tala.aksharas) aksharas, \(tala.groupingLabel)"
        )
    }

    private func kind(at index: Int) -> PulseKind {
        guard tala.beatKinds.indices.contains(index) else { return .normal }
        return PulseKind(tala.beatKinds[index])
    }

    private func color(for kind: PulseKind) -> Color {
        switch kind {
        case .accent: return ThaaLamTheme.saffron
        case .normal: return ThaaLamTheme.teal
        case .ghost: return ThaaLamTheme.lavender
        case .mute: return ThaaLamTheme.outline
        }
    }
}
