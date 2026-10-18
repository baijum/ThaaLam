import SwiftUI
import shared

struct TalasView: View {
    @EnvironmentObject private var vm: MetronomeViewModel
    let onOpenMetronome: () -> Void

    @State private var query = ""
    @State private var filter: TraditionFilter = .all
    @State private var selection: TalaSelection?

    private let allTalas = TalaLibrary.shared.all

    private var filtered: [Tala] {
        let base: [Tala]
        switch filter {
        case .all: base = allTalas
        case .carnatic: base = allTalas.filter { $0.tradition == .carnatic }
        case .hindustani: base = allTalas.filter { $0.tradition == .hindustani }
        }
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return base }
        return base.filter { tala in
            tala.name.localizedCaseInsensitiveContains(trimmed)
                || tala.details.localizedCaseInsensitiveContains(trimmed)
                || tala.groupingLabel.replacingOccurrences(of: " ", with: "").contains(trimmed.replacingOccurrences(of: " ", with: ""))
                || tala.bols.contains { $0.localizedCaseInsensitiveContains(trimmed) }
        }
    }

    var body: some View {
        ZStack {
            ThaaLamTheme.background.ignoresSafeArea()
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    header
                    searchBox
                    HStack(spacing: 8) {
                        ForEach(TraditionFilter.allCases) { option in
                            ChipButton(label: option.label, selected: filter == option) {
                                filter = option
                            }
                        }
                    }
                    Text("\(filtered.count) cycles")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(ThaaLamTheme.textSecondary)
                    ForEach(filtered, id: \.id) { tala in
                        TalaCardView(tala: tala) {
                            selection = TalaSelection(tala: tala)
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 100)
            }
        }
        .onAppear {
            #if DEBUG
            // Development smoke-test hook: open a tala detail directly.
            let args = ProcessInfo.processInfo.arguments
            if selection == nil, args.contains("-talaDetail") {
                let id = args.contains("-talaCarnatic") ? "carnatic.chapu.misra" : "hindustani.teental"
                if let tala = TalaLibrary.shared.find(id: id) {
                    selection = TalaSelection(tala: tala)
                }
            }
            #endif
        }
        .sheet(item: $selection) { selected in
            TalaDetailView(tala: selected.tala) {
                selection = nil
                onOpenMetronome()
            }
            .environmentObject(vm)
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("Tala library")
                .font(.system(size: 28, weight: .black))
                .foregroundStyle(ThaaLamTheme.textPrimary)
                .accessibilityAddTraits(.isHeader)
            Text("35 Suladi Sapta Talas, Chapu cycles and Hindustani theka.")
                .font(.system(size: 12))
                .foregroundStyle(ThaaLamTheme.textSecondary)
        }
    }

    private var searchBox: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(ThaaLamTheme.textSecondary)
            TextField("Search talas, groupings or bols", text: $query)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .foregroundStyle(ThaaLamTheme.textPrimary)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .background(ThaaLamTheme.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(ThaaLamTheme.border, lineWidth: 1)
        )
    }
}

private enum TraditionFilter: String, CaseIterable, Identifiable {
    case all
    case carnatic
    case hindustani

    var id: String { rawValue }

    var label: String {
        switch self {
        case .all: return "All"
        case .carnatic: return "Carnatic"
        case .hindustani: return "Hindustani"
        }
    }
}

private struct TalaSelection: Identifiable {
    let tala: Tala
    var id: String { tala.id }
}

private struct TalaCardView: View {
    let tala: Tala
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(tala.name)
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(ThaaLamTheme.textPrimary)
                        Text("\(tala.tradition.displayName) · \(tala.aksharas) aksharas · \(tala.groupingLabel)")
                            .font(.system(size: 12))
                            .foregroundStyle(ThaaLamTheme.textSecondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(ThaaLamTheme.textSecondary)
                }
                FlowLayout(spacing: 6, lineSpacing: 6) {
                    ForEach(Array(tala.angas.enumerated()), id: \.offset) { entry in
                        let marker = tala.markerAt(index: Int32(angaStart(for: entry.offset)))
                        Text("\(marker ?? "|") \(entry.element.length)")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(ThaaLamTheme.textSecondary)
                            .padding(.horizontal, 9)
                            .padding(.vertical, 4)
                            .background(
                                RoundedRectangle(cornerRadius: 8, style: .continuous)
                                    .fill(ThaaLamTheme.surfaceVariant.opacity(0.6))
                            )
                    }
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(ThaaLamTheme.surface, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .strokeBorder(ThaaLamTheme.border.opacity(0.7), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(tala.name), \(tala.tradition.displayName), \(tala.aksharas) aksharas")
    }

    private func angaStart(for index: Int) -> Int {
        tala.angas.prefix(index).reduce(0) { $0 + Int($1.length) }
    }
}

struct TalaDetailView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var vm: MetronomeViewModel

    let tala: Tala
    let onDone: () -> Void

    @State private var nadaiTicks = 1
    @State private var kalam = 1
    @State private var kalai = 1
    @State private var eduppuAksharas = 0.0
    @State private var didLoadInitialState = false

    private var isLoaded: Bool { vm.settings.talaId == tala.id }
    private var isActive: Bool { isLoaded && vm.playback.isPlaying }

    private var kalamOptions: [Int] {
        TalaFactory.shared.kalamOptionsCsv()
            .split(separator: ",")
            .compactMap { Int($0) }
    }

    private var kalaiOptions: [Int] {
        TalaFactory.shared.kalaiOptionsCsv()
            .split(separator: ",")
            .compactMap { Int($0) }
    }

    private var eduppuOptions: [Double] {
        TalaFactory.shared.eduppuOptionsCsv()
            .split(separator: ",")
            .compactMap { Double($0) }
    }

    private var effectiveBpm: Double {
        min(max(vm.settings.bpm * Double(kalam), 10), 1_600)
    }

    private var activeGesture: AngaGesture {
        guard isActive else { return .none }
        return tala.gestureAt(index: Int32(max(0, vm.playback.beat)))
    }

    private var nadaiOptions: [(ticks: Int, label: String)] {
        if tala.tradition == .carnatic {
            return [
                (1, "None"),
                (3, "Tisra 3"),
                (4, "Chatusra 4"),
                (5, "Khanda 5"),
                (7, "Misra 7"),
                (9, "Sankeerna 9"),
            ]
        }
        return [(1, "One per akshara"), (2, "Eighth"), (3, "Triplet"), (4, "Sixteenth")]
    }

    var body: some View {
        ZStack {
            ThaaLamTheme.background.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    if !tala.details.isEmpty {
                        Text(tala.details)
                            .font(.system(size: 14))
                            .foregroundStyle(ThaaLamTheme.textSecondary)
                    }
                    cycleCard
                    if !tala.bols.isEmpty {
                        thekaCard
                    }
                    kalamCard
                    kalaiCard
                    eduppuCard
                    subdivisionCard
                    tempoCard
                    if isLoaded {
                        Text("Changes apply live · \(playbackOptions.summary)")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(ThaaLamTheme.saffron)
                    }
                    actionButtons
                    savePresetButton
                    Button("Back to library") { dismiss() }
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(ThaaLamTheme.textSecondary)
                        .frame(maxWidth: .infinity)
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 18)
            }
        }
        .presentationDetents([.large])
        .onAppear {
            guard !didLoadInitialState else { return }
            didLoadInitialState = true
            if isLoaded {
                nadaiTicks = Int(vm.settings.subdivision.ticksPerBeat)
                kalam = Int(vm.settings.talaPlayback.kalam)
                kalai = Int(vm.settings.talaPlayback.kalai)
                eduppuAksharas = vm.settings.talaPlayback.eduppuOffsetAksharas
            }
            #if DEBUG
            let args = ProcessInfo.processInfo.arguments
            if args.contains("-talaKalam2") { kalam = 2 }
            if args.contains("-talaKalam4") { kalam = 4 }
            if args.contains("-talaKalai2") { kalai = 2 }
            if args.contains("-talaEduppuHalf") { eduppuAksharas = 0.5 }
            if args.contains("-talaEduppuBefore") { eduppuAksharas = -0.5 }
            if args.contains("-talaAutoStart") {
                vm.applyTala(
                    tala,
                    nadaiTicks: nadaiTicks,
                    kalam: kalam,
                    kalai: kalai,
                    eduppuOffsetAksharas: eduppuAksharas,
                    autoStart: true
                )
                dismiss()
                onDone()
            }
            #endif
        }
    }

    private var playbackOptions: TalaPlayback {
        TalaPlayback(kalam: Int32(kalam), kalai: Int32(kalai), eduppuOffsetAksharas: eduppuAksharas)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(tala.name)
                .font(.system(size: 24, weight: .bold))
                .foregroundStyle(ThaaLamTheme.textPrimary)
            Text("\(tala.tradition.displayName) · \(tala.aksharas) aksharas · \(tala.angaLabel)")
                .font(.system(size: 12))
                .foregroundStyle(ThaaLamTheme.textSecondary)
        }
    }

    private var cycleCard: some View {
        SectionCard(title: "Cycle") {
            VStack(alignment: .leading, spacing: 10) {
                TalaCycleView(
                    tala: tala,
                    activeBeat: isActive ? vm.playback.beat : -1,
                    isPlaying: isActive
                )
                Text("The first pulse is sam. Clap/tali sections are accented; open khali sections are ghosted.")
                    .font(.system(size: 12))
                    .foregroundStyle(ThaaLamTheme.textSecondary)
                if isActive {
                    Text(nowPlayingLine)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(ThaaLamTheme.saffron)
                }
            }
        }
    }

    private var nowPlayingLine: String {
        var parts = ["Now: pulse \(vm.playback.beat + 1)"]
        if let bol = tala.bolAt(index: Int32(max(0, vm.playback.beat))) {
            parts.append(bol)
        }
        if activeGesture != .none {
            parts.append(activeGesture.displayName)
        }
        return parts.joined(separator: " · ")
    }

    private var thekaCard: some View {
        SectionCard(title: "Theka") {
            FlowLayout(spacing: 8, lineSpacing: 8) {
                ForEach(Array(tala.bols.enumerated()), id: \.offset) { entry in
                    let current = isActive && Int(vm.playback.beat) == entry.offset
                    VStack(spacing: 2) {
                        Text(tala.markerAt(index: Int32(entry.offset)) ?? " ")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(ThaaLamTheme.saffron)
                        Text(entry.element)
                            .font(.system(size: 12, weight: current ? .bold : .regular))
                            .foregroundStyle(current ? Color(hex: 0x2A1900) : ThaaLamTheme.textPrimary)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(
                                RoundedRectangle(cornerRadius: 8, style: .continuous)
                                    .fill(current ? ThaaLamTheme.saffron : ThaaLamTheme.surfaceVariant)
                            )
                    }
                }
            }
        }
    }

    private var kalamCard: some View {
        SectionCard(
            title: "Kalam",
            subtitle: "Akshara speed. The base tempo is kept, so 1x always restores it."
        ) {
            VStack(alignment: .leading, spacing: 8) {
                FlowLayout(spacing: 8, lineSpacing: 6) {
                    ForEach(kalamOptions, id: \.self) { option in
                        ChipButton(label: "\(option)x", selected: kalam == option) {
                            kalam = option
                            if isLoaded { vm.setKalam(option) }
                        }
                    }
                }
                Text("Base \(formatBpmStatic(vm.settings.bpm)) BPM → \(formatBpmStatic(effectiveBpm)) BPM at \(kalam)x")
                    .font(.system(size: 12))
                    .foregroundStyle(ThaaLamTheme.textSecondary)
            }
        }
    }

    private var kalaiCard: some View {
        SectionCard(
            title: "Kalai",
            subtitle: "Clicks per akshara. 2-kalai doubles the current nadai."
        ) {
            VStack(alignment: .leading, spacing: 8) {
                FlowLayout(spacing: 8, lineSpacing: 6) {
                    ForEach(kalaiOptions, id: \.self) { option in
                        ChipButton(label: "\(option)-kalai", selected: kalai == option) {
                            kalai = option
                            if isLoaded { vm.setKalai(option) }
                        }
                    }
                }
                Text("\(nadaiTicks * kalai) click\(nadaiTicks * kalai == 1 ? "" : "s") per akshara")
                    .font(.system(size: 12))
                    .foregroundStyle(ThaaLamTheme.textSecondary)
            }
        }
    }

    private var eduppuCard: some View {
        SectionCard(
            title: "Eduppu",
            subtitle: "Where the cycle starts. The count-in ends just before this point."
        ) {
            VStack(alignment: .leading, spacing: 8) {
                FlowLayout(spacing: 8, lineSpacing: 6) {
                    ForEach(eduppuOptions, id: \.self) { option in
                        ChipButton(
                            label: TalaFactory.shared.eduppuLabel(
                                eduppuOffsetAksharas: option
                            ),
                            selected: abs(eduppuAksharas - option) < 0.001
                        ) {
                            eduppuAksharas = option
                            if isLoaded { vm.setEduppu(option) }
                        }
                    }
                }
                HStack {
                    Text("Custom")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(ThaaLamTheme.textSecondary)
                    Spacer()
                    Text(
                        TalaFactory.shared.eduppuLabel(
                            eduppuOffsetAksharas: eduppuAksharas
                        )
                    )
                    .font(.system(size: 12))
                    .foregroundStyle(ThaaLamTheme.textSecondary)
                }
                Slider(
                    value: Binding(
                        get: { eduppuAksharas },
                        set: {
                            eduppuAksharas = (($0 * 4).rounded() / 4)
                        }
                    ),
                    in: -4...4,
                    step: 0.25,
                    onEditingChanged: { editing in
                        if !editing, isLoaded {
                            vm.setEduppu(eduppuAksharas)
                        }
                    }
                )
                .tint(ThaaLamTheme.saffron)
                .accessibilityLabel("Eduppu offset in aksharas")
                .accessibilityValue(
                    TalaFactory.shared.eduppuLabel(
                        eduppuOffsetAksharas: eduppuAksharas
                    )
                )
                Text("Negative is atita (before sam); positive is anagata (after sam).")
                    .font(.system(size: 12))
                    .foregroundStyle(ThaaLamTheme.textSecondary)
            }
        }
    }

    private var subdivisionCard: some View {
        SectionCard(
            title: "Subdivision",
            subtitle: tala.tradition == .carnatic
                ? "Nadai (gathi) multiplies the internal pulse of every akshara"
                : "Theka cycles usually click one pulse per akshara"
        ) {
            FlowLayout(spacing: 8, lineSpacing: 6) {
                ForEach(nadaiOptions, id: \.ticks) { option in
                    ChipButton(label: option.label, selected: nadaiTicks == option.ticks) {
                        nadaiTicks = option.ticks
                        if isLoaded { vm.setSubdivision(ticks: option.ticks) }
                    }
                }
            }
        }
    }

    private var tempoCard: some View {
        SectionCard(title: "Tempo") {
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .lastTextBaseline, spacing: 6) {
                    Text(formatBpmStatic(vm.settings.bpm))
                        .font(.system(size: 42, weight: .bold))
                        .foregroundStyle(ThaaLamTheme.saffron)
                    Text("base BPM")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(ThaaLamTheme.textSecondary)
                }
                if kalam > 1 {
                    Text("Sounds at \(formatBpmStatic(effectiveBpm)) BPM in \(kalam)x kalam")
                        .font(.system(size: 12))
                        .foregroundStyle(ThaaLamTheme.textSecondary)
                }
                Text("The tempo is preserved from your current session; adjust it on the metronome screen.")
                    .font(.system(size: 12))
                    .foregroundStyle(ThaaLamTheme.textSecondary)
            }
        }
    }

    private var actionButtons: some View {
        HStack(spacing: 12) {
            Button("Load") {
                vm.applyTala(
                    tala,
                    nadaiTicks: nadaiTicks,
                    kalam: kalam,
                    kalai: kalai,
                    eduppuOffsetAksharas: eduppuAksharas,
                    autoStart: false
                )
                dismiss()
                onDone()
            }
            .buttonStyle(SecondaryButtonStyle())

            Button(isActive ? "Restart" : "Start") {
                vm.applyTala(
                    tala,
                    nadaiTicks: nadaiTicks,
                    kalam: kalam,
                    kalai: kalai,
                    eduppuOffsetAksharas: eduppuAksharas,
                    autoStart: true
                )
                dismiss()
                onDone()
            }
            .buttonStyle(PrimaryButtonStyle())
        }
    }

    private var savePresetButton: some View {
        Button("Save as a practice preset") {
            if !isLoaded {
                vm.applyTala(
                    tala,
                    nadaiTicks: nadaiTicks,
                    kalam: kalam,
                    kalai: kalai,
                    eduppuOffsetAksharas: eduppuAksharas,
                    autoStart: false
                )
            }
            vm.savePreset(
                name: TalaFactory.shared.drillName(tala: tala, playback: playbackOptions)
            )
        }
        .font(.system(size: 13, weight: .semibold))
        .foregroundStyle(ThaaLamTheme.saffron)
        .frame(maxWidth: .infinity)
    }
}

private func formatBpmStatic(_ bpm: Double) -> String {
    let rounded = (bpm * 10).rounded() / 10
    return rounded.truncatingRemainder(dividingBy: 1) == 0
        ? String(Int(rounded))
        : String(format: "%.1f", rounded)
}
