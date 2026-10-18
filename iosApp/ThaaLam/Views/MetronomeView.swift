import SwiftUI
import shared

struct MetronomeView: View {
    @EnvironmentObject private var vm: MetronomeViewModel
    @State private var showBpmSheet = false
    @State private var showMeterSheet = false
    @State private var practiceExpanded = false
    @State private var flashOpacity = 0.0
    @State private var swingDraft: Double?

    private var displayBpm: Double {
        if vm.playback.isPlaying && vm.settings.rampEnabled && vm.playback.effectiveBpm > 0 {
            return vm.playback.effectiveBpm
        }
        return vm.settings.bpm
    }

    var body: some View {
        ZStack {
            ThaaLamTheme.background.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 16) {
                    header
                    tempoCard
                    transportRow
                    pulseCard
                    grooveCard
                    practiceCard
                    soundCard
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 100)
            }
            if flashOpacity > 0 {
                ThaaLamTheme.saffron
                    .opacity(flashOpacity)
                    .ignoresSafeArea()
                    .allowsHitTesting(false)
            }
        }
        .sheet(isPresented: $showBpmSheet) {
            BpmSheet(current: vm.settings.bpm) { value in
                vm.setBpm(value)
                showBpmSheet = false
            }
        }
        .sheet(isPresented: $showMeterSheet) {
            MeterSheet(currentBeats: Int(vm.settings.meter.beatsPerBar),
                       currentUnit: Int(vm.settings.meter.beatUnit)) { beats, unit, grouping in
                vm.setMeter(beats: beats, unit: unit, grouping: grouping)
                showMeterSheet = false
            }
        }
        .onChange(of: vm.visualPlayback.beat) { _ in triggerFlash() }
        .onChange(of: vm.visualPlayback.bar) { _ in triggerFlash() }
    }

    private var header: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("ThaaLam")
                    .font(.system(size: 28, weight: .black))
                    .foregroundStyle(ThaaLamTheme.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                Text("Rhythm, precisely.")
                    .font(.system(size: 12))
                    .foregroundStyle(ThaaLamTheme.textSecondary)
            }
            Spacer()
            if let tala = vm.currentTala {
                Button {
                    vm.clearTala()
                } label: {
                    HStack(spacing: 5) {
                        Text(tala.name)
                            .font(.system(size: 12, weight: .semibold))
                        Image(systemName: "xmark")
                            .font(.system(size: 9, weight: .bold))
                    }
                    .foregroundStyle(ThaaLamTheme.saffron)
                    .padding(.horizontal, 11)
                    .padding(.vertical, 7)
                    .background(
                        Capsule().fill(ThaaLamTheme.saffron.opacity(0.12))
                    )
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Tala mode: \(tala.name). Double tap to leave tala mode")
            }
        }
    }

    private var tempoCard: some View {
        SectionCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(tempoMarking(displayBpm))
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(ThaaLamTheme.saffron)
                        Text(vm.currentTala?.name ?? "Free meter · \(vm.settings.meter.label)")
                            .font(.system(size: 12))
                            .foregroundStyle(ThaaLamTheme.textSecondary)
                    }
                    Spacer()
                    if vm.playback.isPlaying {
                        Text("LIVE")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(Color(hex: 0x00201D))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(Capsule().fill(ThaaLamTheme.teal))
                    }
                }
                HStack(alignment: .lastTextBaseline, spacing: 8) {
                    Text(formatBpm(displayBpm))
                        .font(.system(size: 82, weight: .black, design: .default))
                        .foregroundStyle(ThaaLamTheme.saffron)
                        .minimumScaleFactor(0.6)
                        .lineLimit(1)
                        .contentShape(Rectangle())
                        .onTapGesture { showBpmSheet = true }
                        .accessibilityLabel("Tempo")
                        .accessibilityValue("\(Int(displayBpm.rounded())) beats per minute")
                        .accessibilityHint("Double tap to type a tempo")
                    Text("BPM")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(ThaaLamTheme.textSecondary)
                        .padding(.bottom, 12)
                }
                Slider(value: bpmSliderBinding, in: 0...1)
                    .tint(ThaaLamTheme.saffron)
                    .accessibilityLabel("Tempo slider")
                    .accessibilityValue("\(Int(vm.settings.bpm.rounded())) beats per minute")
                HStack(spacing: 8) {
                    tempoButton("−1") { vm.nudgeBpm(-1) }
                    tempoButton("+1") { vm.nudgeBpm(1) }
                    tempoButton("½×") { vm.halfTempo() }
                    tempoButton("2×") { vm.doubleTempo() }
                }
            }
        }
    }

    private var pulseCard: some View {
        SectionCard(
            title: "Pulse map",
            subtitle: vm.currentTala.map {
                "\($0.name) · \($0.groupingLabel) · \($0.aksharas) aksharas"
            } ?? "Tap any pulse to cycle accent → normal → ghost → mute.",
            trailing: {
                Button("Group accents") { vm.applyGroupAccents() }
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(ThaaLamTheme.saffron)
            }
        ) {
            VStack(alignment: .leading, spacing: 12) {
                BeatGridView(
                    settings: vm.settings,
                    playback: vm.visualPlayback,
                    markers: markers,
                    bols: vm.currentTala?.bols ?? [],
                    onTap: { vm.cycleBeat($0) }
                )
                statusLine
            }
        }
    }

    private var statusLine: some View {
        Text(statusText)
            .font(.system(size: 12))
            .foregroundStyle(ThaaLamTheme.textSecondary)
    }

    private var statusText: String {
        let playback = vm.playback
        if !playback.isPlaying { return "Ready" }
        if playback.isCountIn {
            return "Count-in · \(playback.countInRemaining) bar(s) to go"
        }
        return "Bar \(playback.bar) · Pulse \(playback.beat + 1) · \(formatBpm(playback.effectiveBpm)) BPM"
    }

    private var transportRow: some View {
        HStack(spacing: 12) {
            Button {
                vm.togglePlayback()
            } label: {
                ZStack {
                    Circle()
                        .fill(vm.playback.isPlaying ? ThaaLamTheme.coral : ThaaLamTheme.saffron)
                    Image(systemName: vm.playback.isPlaying ? "stop.fill" : "play.fill")
                        .font(.system(size: 34, weight: .semibold))
                        .foregroundStyle(Color(hex: 0x2A1900))
                }
                .frame(width: 88, height: 88)
                .scaleEffect(vm.playback.isPlaying ? 1.03 : 1)
                .animation(.easeOut(duration: 0.14), value: vm.playback.isPlaying)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(vm.playback.isPlaying ? "Stop metronome" : "Start metronome")

            Button {
                vm.tapTempo()
            } label: {
                VStack(spacing: 4) {
                    Text("TAP TEMPO")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(ThaaLamTheme.textPrimary)
                    Text(tapHint)
                        .font(.system(size: 11))
                        .foregroundStyle(ThaaLamTheme.textSecondary)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity, minHeight: 88)
                .background(ThaaLamTheme.teal.opacity(0.16))
                .overlay(
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .strokeBorder(ThaaLamTheme.teal.opacity(0.5), lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Tap tempo")
            .accessibilityHint("Tap repeatedly to set the tempo")
            .accessibilityValue(tapHint)
        }
    }

    private var tapHint: String {
        guard let tap = vm.tapState else { return "Tap four or more times" }
        if let bpm = tap.bpm?.doubleValue {
            return "\(tap.tapCount) taps · \(formatBpm(bpm)) BPM"
        }
        if tap.tapCount <= 1 { return "Keep tapping…" }
        return "\(tap.tapCount) taps"
    }

    private var grooveCard: some View {
        SectionCard(
            title: "Groove",
            subtitle: "\(vm.settings.meter.label) · \(vm.settings.meter.groupingLabel)",
            trailing: {
                Button("Custom") { showMeterSheet = true }
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(ThaaLamTheme.saffron)
            }
        ) {
            VStack(alignment: .leading, spacing: 10) {
                FieldLabel("Meter")
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(meterPresets, id: \.label) { preset in
                            ChipButton(
                                label: preset.label,
                                selected: Int(vm.settings.meter.beatsPerBar) == preset.beats
                                    && Int(vm.settings.meter.beatUnit) == preset.unit
                            ) {
                                vm.setMeter(beats: preset.beats, unit: preset.unit)
                            }
                        }
                    }
                    .padding(.vertical, 2)
                }

                FieldLabel(vm.currentTala == nil ? "Subdivision" : "Subdivision · Nadai")
                FlowLayout(spacing: 8, lineSpacing: 6) {
                    ForEach(subdivisionOptions, id: \.ticks) { option in
                        ChipButton(
                            label: option.label,
                            selected: Int(vm.settings.subdivision.ticksPerBeat) == option.ticks
                        ) {
                            vm.setSubdivision(ticks: option.ticks)
                        }
                    }
                }

                HStack {
                    FieldLabel("Swing")
                    Spacer()
                    Text(swingLabel(swingDraft ?? vm.settings.swingPercent))
                        .font(.system(size: 12))
                        .foregroundStyle(ThaaLamTheme.textSecondary)
                }
                Slider(
                    value: Binding(
                        get: { swingDraft ?? vm.settings.swingPercent },
                        set: { swingDraft = $0 }
                    ),
                    in: 50...75,
                    onEditingChanged: { editing in
                        if !editing, let draft = swingDraft {
                            vm.setSwing(draft)
                            swingDraft = nil
                        }
                    }
                )
                .tint(ThaaLamTheme.saffron)
                .disabled(vm.settings.subdivision.ticksPerBeat < 2)
                .opacity(vm.settings.subdivision.ticksPerBeat < 2 ? 0.4 : 1)
                Text(
                    vm.settings.subdivision.ticksPerBeat < 2
                        ? "Choose an eighth-note subdivision or finer to swing."
                        : "50% straight · 66.7% triplet shuffle · 75% dotted"
                )
                .font(.system(size: 11))
                .foregroundStyle(ThaaLamTheme.textSecondary)
            }
        }
    }

    private var practiceCard: some View {
        SectionCard(
            title: "Practice",
            subtitle: practiceSummary,
            trailing: {
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        practiceExpanded.toggle()
                    }
                } label: {
                    Image(systemName: practiceExpanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(ThaaLamTheme.textSecondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(practiceExpanded ? "Collapse practice tools" : "Expand practice tools")
            }
        ) {
            VStack(alignment: .leading, spacing: 10) {
                StepperRow(
                    label: "Count-in",
                    supportingText: "Bars of clicks before the pattern starts",
                    value: "\(vm.settings.countInBars)"
                ) {
                    vm.setCountInBars(Int(vm.settings.countInBars) - 1)
                } onIncrease: {
                    vm.setCountInBars(Int(vm.settings.countInBars) + 1)
                }

                if practiceExpanded {
                    Divider().overlay(ThaaLamTheme.border)
                    StepperRow(
                        label: "Gap · play bars",
                        supportingText: "Click for this many bars, then go silent",
                        value: "\(vm.settings.gapPlayBars)"
                    ) {
                        vm.setGap(playBars: Int(vm.settings.gapPlayBars) - 1,
                                  silentBars: Int(vm.settings.gapSilentBars))
                    } onIncrease: {
                        vm.setGap(playBars: Int(vm.settings.gapPlayBars) + 1,
                                  silentBars: Int(vm.settings.gapSilentBars))
                    }
                    StepperRow(
                        label: "Gap · silent bars",
                        supportingText: "Keep the clock running without clicks",
                        value: "\(vm.settings.gapSilentBars)"
                    ) {
                        vm.setGap(playBars: Int(vm.settings.gapPlayBars),
                                  silentBars: Int(vm.settings.gapSilentBars) - 1)
                    } onIncrease: {
                        vm.setGap(playBars: Int(vm.settings.gapPlayBars),
                                  silentBars: Int(vm.settings.gapSilentBars) + 1)
                    }
                    if vm.settings.isGapTraining {
                        Button("Turn gap training off") {
                            vm.setGap(playBars: 0, silentBars: 0)
                        }
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(ThaaLamTheme.saffron)
                    }

                    HStack {
                        FieldLabel("Random mute")
                        Spacer()
                        Text("\(Int((vm.settings.muteProbability * 100).rounded()))%")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(ThaaLamTheme.textPrimary)
                    }
                    Slider(
                        value: Binding(
                            get: { vm.settings.muteProbability },
                            set: { vm.setMuteProbability($0) }
                        ),
                        in: 0...1
                    )
                    .tint(ThaaLamTheme.saffron)

                    ToggleRow(
                        title: "Protect the sam",
                        subtitle: "Never drop the first pulse of a bar",
                        isOn: Binding(
                            get: { vm.settings.protectFirstBeat },
                            set: { vm.setProtectFirstBeat($0) }
                        )
                    )

                    ToggleRow(
                        title: "Tempo ramp",
                        subtitle: "Step the BPM up or down while you practise",
                        isOn: Binding(
                            get: { vm.settings.rampEnabled },
                            set: {
                                vm.setRamp(
                                    enabled: $0,
                                    targetBpm: vm.settings.rampTargetBpm,
                                    stepBpm: vm.settings.rampStepBpm,
                                    everyBars: Int(vm.settings.rampEveryBars)
                                )
                            }
                        )
                    )

                    if vm.settings.rampEnabled {
                        StepperRow(
                            label: "Target BPM",
                            value: formatBpm(vm.settings.rampTargetBpm)
                        ) {
                            vm.setRamp(enabled: true,
                                       targetBpm: vm.settings.rampTargetBpm - 5,
                                       stepBpm: vm.settings.rampStepBpm,
                                       everyBars: Int(vm.settings.rampEveryBars))
                        } onIncrease: {
                            vm.setRamp(enabled: true,
                                       targetBpm: vm.settings.rampTargetBpm + 5,
                                       stepBpm: vm.settings.rampStepBpm,
                                       everyBars: Int(vm.settings.rampEveryBars))
                        }
                        StepperRow(
                            label: "Step size",
                            value: formatBpm(vm.settings.rampStepBpm)
                        ) {
                            vm.setRamp(enabled: true,
                                       targetBpm: vm.settings.rampTargetBpm,
                                       stepBpm: vm.settings.rampStepBpm - 1,
                                       everyBars: Int(vm.settings.rampEveryBars))
                        } onIncrease: {
                            vm.setRamp(enabled: true,
                                       targetBpm: vm.settings.rampTargetBpm,
                                       stepBpm: vm.settings.rampStepBpm + 1,
                                       everyBars: Int(vm.settings.rampEveryBars))
                        }
                        StepperRow(
                            label: "Every (bars)",
                            value: "\(vm.settings.rampEveryBars)"
                        ) {
                            vm.setRamp(enabled: true,
                                       targetBpm: vm.settings.rampTargetBpm,
                                       stepBpm: vm.settings.rampStepBpm,
                                       everyBars: Int(vm.settings.rampEveryBars) - 1)
                        } onIncrease: {
                            vm.setRamp(enabled: true,
                                       targetBpm: vm.settings.rampTargetBpm,
                                       stepBpm: vm.settings.rampStepBpm,
                                       everyBars: Int(vm.settings.rampEveryBars) + 1)
                        }
                    }
                }
            }
        }
    }

    private var soundCard: some View {
        SectionCard(
            title: "Sound",
            subtitle: "Synthesised on the audio thread — no sample latency",
            trailing: {
                Button {
                    vm.preview()
                } label: {
                    Image(systemName: "speaker.wave.2.fill")
                        .foregroundStyle(ThaaLamTheme.saffron)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Preview current click sound")
            }
        ) {
            VStack(alignment: .leading, spacing: 10) {
                FlowLayout(spacing: 8, lineSpacing: 6) {
                    ForEach(ClickSound.allCases) { sound in
                        ChipButton(
                            label: sound.displayName,
                            selected: ClickSound(vm.settings.soundSet) == sound
                        ) {
                            vm.setSound(sound)
                        }
                    }
                }
                HStack {
                    FieldLabel("Volume")
                    Spacer()
                    Text("\(Int((vm.settings.volume * 100).rounded()))%")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(ThaaLamTheme.textPrimary)
                }
                Slider(
                    value: Binding(
                        get: { Double(vm.settings.volume) },
                        set: { vm.setVolume($0) }
                    ),
                    in: 0...1
                )
                .tint(ThaaLamTheme.saffron)
            }
        }
    }

    // MARK: - Helpers

    private var bpmSliderBinding: Binding<Double> {
        Binding(
            get: {
                log(max(10, vm.settings.bpm) / 10.0) / log(40.0)
            },
            set: { value in
                let bpm = 10.0 * pow(40.0, min(max(value, 0), 1))
                vm.setBpm((bpm * 10).rounded() / 10)
            }
        )
    }

    private var markers: [String?] {
        guard let tala = vm.currentTala else { return [] }
        return (0..<Int(tala.aksharas)).map { tala.markerAt(index: Int32($0)) }
    }

    private var practiceSummary: String {
        var parts = ["\(vm.settings.countInBars) count-in"]
        if vm.settings.isGapTraining {
            parts.append("\(vm.settings.gapPlayBars) on / \(vm.settings.gapSilentBars) off")
        }
        if vm.settings.muteProbability > 0 {
            parts.append("\(Int((vm.settings.muteProbability * 100).rounded()))% mute")
        }
        if vm.settings.rampEnabled {
            parts.append("ramp to \(formatBpm(vm.settings.rampTargetBpm))")
        }
        return parts.joined(separator: " · ")
    }

    private func triggerFlash() {
        guard vm.uiPreferences.flashEnabled,
              vm.visualPlayback.isPlaying,
              !vm.visualPlayback.isCountIn else { return }
        flashOpacity = 0.13
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.055) {
            flashOpacity = 0
        }
    }

    private func tempoButton(_ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 14, weight: .semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 9)
                .background(ThaaLamTheme.surfaceVariant)
                .foregroundStyle(ThaaLamTheme.textPrimary)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

private struct MeterPreset {
    let beats: Int
    let unit: Int
    var label: String { "\(beats)/\(unit)" }
}

private let meterPresets: [MeterPreset] = [
    MeterPreset(beats: 2, unit: 4),
    MeterPreset(beats: 3, unit: 4),
    MeterPreset(beats: 4, unit: 4),
    MeterPreset(beats: 5, unit: 4),
    MeterPreset(beats: 6, unit: 8),
    MeterPreset(beats: 7, unit: 8),
    MeterPreset(beats: 9, unit: 8),
    MeterPreset(beats: 12, unit: 8),
]

private let subdivisionOptions: [(ticks: Int, label: String)] = [
    (1, "1/4"),
    (2, "1/8"),
    (3, "1/8T"),
    (4, "1/16"),
    (5, "1/16Q"),
    (6, "1/16S"),
    (7, "1/16P"),
    (8, "1/32"),
    (9, "1/16N"),
]

private func formatBpm(_ bpm: Double) -> String {
    let rounded = (bpm * 10).rounded() / 10
    return rounded.truncatingRemainder(dividingBy: 1) == 0
        ? String(Int(rounded))
        : String(format: "%.1f", rounded)
}

private func tempoMarking(_ bpm: Double) -> String {
    switch bpm {
    case ..<20: return "Larghissimo"
    case ..<40: return "Grave"
    case ..<60: return "Largo"
    case ..<66: return "Lento"
    case ..<76: return "Adagio"
    case ..<108: return "Andante"
    case ..<120: return "Moderato"
    case ..<156: return "Allegro"
    case ..<176: return "Vivace"
    case ..<200: return "Presto"
    default: return "Prestissimo"
    }
}

private func swingLabel(_ percent: Double) -> String {
    switch percent {
    case ..<50.5: return "Straight"
    case ..<60: return "\(Int(percent.rounded()))% · light shuffle"
    case ..<69: return "\(Int(percent.rounded()))% · triplet swing"
    default: return "\(Int(percent.rounded()))% · dotted"
    }
}

private struct BpmSheet: View {
    @Environment(\.dismiss) private var dismiss
    let current: Double
    let onApply: (Double) -> Void
    @State private var text: String

    init(current: Double, onApply: @escaping (Double) -> Void) {
        self.current = current
        self.onApply = onApply
        _text = State(initialValue: formatBpm(current))
    }

    private var parsed: Double? {
        Double(text.replacingOccurrences(of: ",", with: "."))
    }

    private var valid: Bool {
        guard let parsed else { return false }
        return parsed >= 10 && parsed <= 400
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Beats per minute") {
                    TextField("Tempo", text: $text)
                        .keyboardType(.decimalPad)
                        .font(.system(size: 28, weight: .bold))
                    Text("10 – 400 BPM, decimals welcome")
                        .font(.system(size: 12))
                        .foregroundStyle(ThaaLamTheme.textSecondary)
                }
            }
            .navigationTitle("Set tempo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Set") {
                        if let parsed { onApply(parsed) }
                        dismiss()
                    }
                    .disabled(!valid)
                }
            }
        }
        .presentationDetents([.height(260)])
    }
}

private struct MeterSheet: View {
    @Environment(\.dismiss) private var dismiss
    let currentBeats: Int
    let currentUnit: Int
    let onApply: (Int, Int, String) -> Void
    @State private var beats: Int
    @State private var unit: Int
    @State private var grouping: String = ""

    init(currentBeats: Int, currentUnit: Int, onApply: @escaping (Int, Int, String) -> Void) {
        self.currentBeats = currentBeats
        self.currentUnit = currentUnit
        self.onApply = onApply
        _beats = State(initialValue: currentBeats)
        _unit = State(initialValue: currentUnit)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Pulses per bar") {
                    Stepper(value: $beats, in: 1...32) {
                        Text("\(beats)")
                            .font(.system(size: 20, weight: .semibold))
                    }
                }
                Section("Beat unit") {
                    Picker("Beat unit", selection: $unit) {
                        ForEach([2, 4, 8, 16], id: \.self) { value in
                            Text("1/\(value)").tag(value)
                        }
                    }
                    .pickerStyle(.segmented)
                }
                Section("Grouping") {
                    TextField("2+2+3", text: $grouping)
                        .keyboardType(.numbersAndPunctuation)
                    Text("Leave blank for the musically standard grouping.")
                        .font(.system(size: 12))
                        .foregroundStyle(ThaaLamTheme.textSecondary)
                }
            }
            .navigationTitle("Custom meter")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Apply") {
                        onApply(beats, unit, grouping)
                        dismiss()
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
