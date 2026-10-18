import QuartzCore
import SwiftUI
import shared

struct PracticeView: View {
    @EnvironmentObject private var vm: MetronomeViewModel
    @State private var confirmClear = false
    @State private var tapTouchActive = false

    private var nowMs: Int64 {
        Int64(Date().timeIntervalSince1970 * 1_000.0)
    }

    private var stats: PracticeStats {
        PracticeStatsCalculator.shared.compute(
            data: vm.practiceData,
            nowEpochMs: nowMs,
            zoneId: TimeZone.current.identifier
        )
    }

    private var tapSummary: TapAccuracySummary? {
        vm.tapAccuracy.summary
    }

    var body: some View {
        ZStack {
            ThaaLamTheme.background.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    todayCard
                    weekCard
                    goalsCard
                    tapCard
                    bestTempoCard
                    sessionCard
                    exportCard
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 100)
            }
        }
        .alert("Clear practice history?", isPresented: $confirmClear) {
            Button("Clear", role: .destructive) { vm.clearPracticeHistory() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes every recorded session and tap summary from this device. Goals are kept.")
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("Practice")
                .font(.system(size: 28, weight: .black))
                .foregroundStyle(ThaaLamTheme.textPrimary)
                .accessibilityAddTraits(.isHeader)
            Text("Local goals, honest feedback and a log that stays on this device.")
                .font(.system(size: 12))
                .foregroundStyle(ThaaLamTheme.textSecondary)
        }
    }

    private var todayCard: some View {
        SectionCard(
            title: "Today",
            subtitle: stats.hasSessions
                ? "\(stats.streakDays)-day streak · \(stats.sessionCount) logged"
                : "Start the metronome to record your first session",
            trailing: {
                Text("\(Int((stats.todayProgress * 100).rounded()))%")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(ThaaLamTheme.saffron)
            }
        ) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .lastTextBaseline, spacing: 6) {
                    Text(formatMinutes(stats.todayMinutes))
                        .font(.system(size: 42, weight: .bold))
                        .foregroundStyle(ThaaLamTheme.saffron)
                    Text("/ \(stats.dailyGoalMinutes) min")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(ThaaLamTheme.textSecondary)
                }
                ProgressView(value: stats.todayProgress)
                    .tint(ThaaLamTheme.saffron)
                HStack(spacing: 24) {
                    statPill("This week", formatMinutes(stats.weekMinutes))
                    statPill("All time", formatMinutes(stats.totalMinutes))
                }
            }
        }
    }

    private var weekCard: some View {
        let maxMinutes = max(1, stats.recentDays.map { $0.minutes }.max() ?? 1)
        return SectionCard(
            title: "Last seven days",
            subtitle: "Minutes by local day; overnight sessions are split at midnight",
            trailing: {
                Text("\(formatMinutes(stats.weekMinutes)) / \(stats.weeklyGoalMinutes) min")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(ThaaLamTheme.textSecondary)
            }
        ) {
            HStack(alignment: .bottom, spacing: 8) {
                ForEach(Array(stats.recentDays.enumerated()), id: \.offset) { _, day in
                    dayBar(day: day, maxMinutes: maxMinutes)
                }
            }
            .frame(height: 90)
        }
    }

    private func dayBar(day: PracticeDay, maxMinutes: Double) -> some View {
        let ratio = min(max(day.minutes / maxMinutes, 0), 1)
        let height = 8 + 62 * ratio
        let label = shortDay(day.date)
        return VStack(spacing: 4) {
            Spacer(minLength: 0)
            RoundedRectangle(cornerRadius: 5, style: .continuous)
                .fill(day.minutes > 0 ? ThaaLamTheme.saffron : ThaaLamTheme.surfaceVariant)
                .frame(width: 18, height: height)
                .accessibilityLabel("\(day.date): \(formatMinutes(day.minutes))")
            Text(label)
                .font(.system(size: 10, weight: day.isToday ? .bold : .regular))
                .foregroundStyle(day.isToday ? ThaaLamTheme.saffron : ThaaLamTheme.textSecondary)
        }
        .frame(maxWidth: .infinity)
    }

    private var goalsCard: some View {
        SectionCard(
            title: "Goals",
            subtitle: "A gentle target, not a streak trap"
        ) {
            VStack(alignment: .leading, spacing: 10) {
                StepperRow(
                    label: "Daily goal (min)",
                    supportingText: "Counted from local midnight",
                    value: "\(stats.dailyGoalMinutes)"
                ) {
                    vm.setPracticeGoals(
                        dailyMinutes: max(5, Int(stats.dailyGoalMinutes) - 5),
                        weeklyMinutes: Int(stats.weeklyGoalMinutes)
                    )
                } onIncrease: {
                    vm.setPracticeGoals(
                        dailyMinutes: min(240, Int(stats.dailyGoalMinutes) + 5),
                        weeklyMinutes: Int(stats.weeklyGoalMinutes)
                    )
                }
                StepperRow(
                    label: "Weekly goal (min)",
                    supportingText: "Monday to Sunday",
                    value: "\(stats.weeklyGoalMinutes)"
                ) {
                    vm.setPracticeGoals(
                        dailyMinutes: Int(stats.dailyGoalMinutes),
                        weeklyMinutes: max(Int(stats.dailyGoalMinutes), Int(stats.weeklyGoalMinutes) - 15)
                    )
                } onIncrease: {
                    vm.setPracticeGoals(
                        dailyMinutes: Int(stats.dailyGoalMinutes),
                        weeklyMinutes: min(1_680, Int(stats.weeklyGoalMinutes) + 15)
                    )
                }
            }
        }
    }

    private var tapCard: some View {
        SectionCard(
            title: "Tap accuracy",
            subtitle: "Tap the pad with the main pulse while the metronome is running",
            trailing: {
                Text(vm.playback.isPlaying && !vm.playback.isCountIn ? "LISTENING" : "IDLE")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(
                        vm.playback.isPlaying && !vm.playback.isCountIn
                            ? ThaaLamTheme.teal
                            : ThaaLamTheme.textSecondary
                    )
            }
        ) {
            VStack(alignment: .leading, spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .fill(
                            vm.playback.isPlaying && !vm.playback.isCountIn
                                ? ThaaLamTheme.saffron.opacity(0.14)
                                : ThaaLamTheme.surfaceVariant.opacity(0.5)
                        )
                    VStack(spacing: 4) {
                        Text(
                            vm.playback.isCountIn && vm.playback.isPlaying
                                ? "Count-in…"
                                : (vm.playback.isPlaying ? "TAP" : "Start the metronome")
                        )
                        .font(.system(size: 28, weight: .black))
                        .foregroundStyle(ThaaLamTheme.saffron)
                        Text(lastOffsetLabel(vm.tapAccuracy.lastOffsetMs))
                            .font(.system(size: 13))
                            .foregroundStyle(ThaaLamTheme.textSecondary)
                    }
                }
                .frame(height: 140)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { _ in
                            guard !tapTouchActive else { return }
                            tapTouchActive = true
                            if vm.playback.isPlaying && !vm.playback.isCountIn {
                                vm.gradeTap(atHostMs: CACurrentMediaTime() * 1_000.0)
                            }
                        }
                        .onEnded { _ in tapTouchActive = false }
                )
                .accessibilityLabel("Tap accuracy pad")
                .accessibilityHint("Tap with the main pulse while the metronome is playing")

                if let tap = tapSummary {
                    tapSummaryView(tap)
                } else {
                    Text("Tap along to see your signed error, consistency and early/late tendency.")
                        .font(.system(size: 12))
                        .foregroundStyle(ThaaLamTheme.textSecondary)
                }

                HStack {
                    FieldLabel("Calibration")
                    Spacer()
                    Text("\(vm.uiPreferences.tapCalibrationMs) ms")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(ThaaLamTheme.textPrimary)
                }
                Slider(
                    value: Binding(
                        get: { Double(vm.uiPreferences.tapCalibrationMs) },
                        set: { vm.setTapCalibration(Int(($0 / 10).rounded()) * 10) }
                    ),
                    in: -100...200,
                    step: 10
                )
                .tint(ThaaLamTheme.saffron)
                Text("Increase this if the grader reads your taps as consistently early; decrease it if they read late. Output latency is estimated automatically; calibration is the final touch-to-sound correction.")
                    .font(.system(size: 12))
                    .foregroundStyle(ThaaLamTheme.textSecondary)
            }
        }
    }

    private func tapSummaryView(_ tap: TapAccuracySummary) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 20) {
                statPill("Taps", "\(tap.tapCount)")
                statPill("Mean |error|", "\(formatMs(tap.meanAbsoluteOffsetMs)) ms")
                statPill("On time", "\(Int(tap.onTimePercent.rounded()))%")
            }
            HStack(spacing: 20) {
                statPill("Rating", tap.rating)
                statPill("Tendency", tap.tendency)
                statPill("Bias", "\(formatSignedMs(tap.meanOffsetMs)) ms")
            }
            histogram(tap)
        }
    }

    private func histogram(_ tap: TapAccuracySummary) -> some View {
        let values = tap.histogram.map { Int($0.intValue) }
        let maxValue = max(1, values.max() ?? 1)
        return VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .bottom, spacing: 2) {
                ForEach(Array(values.enumerated()), id: \.offset) { index, count in
                    let offset = Double(index * 10 - 100)
                    RoundedRectangle(cornerRadius: 2, style: .continuous)
                        .fill(
                            abs(offset) <= tap.toleranceMs
                                ? ThaaLamTheme.saffron
                                : ThaaLamTheme.teal.opacity(0.65)
                        )
                        .frame(maxWidth: .infinity)
                        .frame(height: 4 + 40 * Double(count) / Double(maxValue))
                }
            }
            .frame(height: 54, alignment: .bottom)
            HStack {
                Text("−100 ms")
                Spacer()
                Text("0")
                Spacer()
                Text("+100 ms")
            }
            .font(.system(size: 10))
            .foregroundStyle(ThaaLamTheme.textSecondary)
        }
    }

    private var bestTempoCard: some View {
        let entries = (stats.bestByTala.map { ("Tala · \($0.label)", $0) }
            + stats.bestByPreset.map { ("Preset · \($0.label)", $0) })
            .sorted { $0.1.bpm > $1.1.bpm }
            .prefix(5)
        return SectionCard(
            title: "Best tempo",
            subtitle: "Highest effective BPM reached in a recorded session"
        ) {
            if entries.isEmpty {
                Text("Load a tala or preset, play, and your best tempo will appear here.")
                    .font(.system(size: 12))
                    .foregroundStyle(ThaaLamTheme.textSecondary)
            } else {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(Array(entries), id: \.0) { entry in
                        HStack {
                            Text(entry.0)
                                .font(.system(size: 14))
                                .foregroundStyle(ThaaLamTheme.textPrimary)
                            Spacer()
                            Text("\(formatBpm(entry.1.bpm)) BPM · \(entry.1.sessions)×")
                                .font(.system(size: 12))
                                .foregroundStyle(ThaaLamTheme.textSecondary)
                        }
                    }
                }
            }
        }
    }

    private var sessionCard: some View {
        SectionCard(
            title: "Recent sessions",
            subtitle: vm.practiceData.sessions.isEmpty
                ? "Nothing recorded yet"
                : "\(vm.practiceData.sessions.count) kept on this device · newest first",
            trailing: {
                if !vm.practiceData.sessions.isEmpty {
                    Button("Clear") { confirmClear = true }
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(ThaaLamTheme.saffron)
                }
            }
        ) {
            if vm.practiceData.sessions.isEmpty {
                Text("Each start-to-stop run is recorded with tempo, meter or tala, practice modes and tap accuracy.")
                    .font(.system(size: 12))
                    .foregroundStyle(ThaaLamTheme.textSecondary)
            } else {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(Array(vm.practiceData.sessions.prefix(6)), id: \.id) { session in
                        sessionRow(session)
                    }
                }
            }
        }
    }

    private func sessionRow(_ session: PracticeSession) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack {
                Text(session.contextLabel)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(ThaaLamTheme.textPrimary)
                Spacer()
                Text(formatDuration(session.durationMs))
                    .font(.system(size: 12))
                    .foregroundStyle(ThaaLamTheme.saffron)
            }
            Text("\(formatBpm(session.effectiveStartBpm)) BPM · \(session.modeLabel)")
                .font(.system(size: 12))
                .foregroundStyle(ThaaLamTheme.textSecondary)
            if let tap = session.tap {
                Text("Tap \(tap.rating.lowercased()) · \(formatSignedMs(tap.meanOffsetMs)) ms · \(Int(tap.onTimePercent.rounded()))% on time")
                    .font(.system(size: 10))
                    .foregroundStyle(ThaaLamTheme.textSecondary)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(ThaaLamTheme.surfaceVariant.opacity(0.45))
        )
    }

    private var exportCard: some View {
        SectionCard(
            title: "Export",
            subtitle: "Share a local file; nothing is uploaded"
        ) {
            HStack(spacing: 10) {
                ShareLink(item: vm.practiceCsv()) {
                    Text("CSV")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(SecondaryButtonStyle())
                ShareLink(item: vm.practiceJson()) {
                    Text("JSON")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(SecondaryButtonStyle())
            }
            Text("The CSV is spreadsheet-friendly; the JSON is a complete backup of sessions and goals.")
                .font(.system(size: 12))
                .foregroundStyle(ThaaLamTheme.textSecondary)
        }
    }

    private func statPill(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(label)
                .font(.system(size: 10))
                .foregroundStyle(ThaaLamTheme.textSecondary)
            Text(value)
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(ThaaLamTheme.textPrimary)
        }
    }

    private func shortDay(_ iso: String) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        guard let date = formatter.date(from: iso) else { return String(iso.suffix(2)) }
        formatter.dateFormat = "EE"
        return String(formatter.string(from: date).prefix(2))
    }

    private func lastOffsetLabel(_ offset: Double?) -> String {
        guard let offset else { return "No tap graded yet" }
        if offset > 15 { return "Late by \(formatMs(offset)) ms" }
        if offset < -15 { return "Early by \(formatMs(abs(offset))) ms" }
        return "On time · \(formatSignedMs(offset)) ms"
    }

    private func formatMinutes(_ minutes: Double) -> String {
        if minutes <= 0 { return "0m" }
        if minutes < 1 { return "<1m" }
        let total = Int(minutes.rounded())
        let hours = total / 60
        let mins = total % 60
        return hours > 0 ? "\(hours)h \(mins)m" : "\(mins)m"
    }

    private func formatDuration(_ ms: Int64) -> String {
        let seconds = max(0, ms / 1_000)
        let minutes = seconds / 60
        let secs = seconds % 60
        return minutes > 0 ? "\(minutes)m \(secs)s" : "\(secs)s"
    }

    private func formatBpm(_ value: Double) -> String {
        let rounded = (value * 10).rounded() / 10
        return rounded.truncatingRemainder(dividingBy: 1) == 0
            ? String(Int(rounded))
            : String(format: "%.1f", rounded)
    }

    private func formatMs(_ value: Double) -> String {
        let rounded = (abs(value) * 10).rounded() / 10
        return rounded.truncatingRemainder(dividingBy: 1) == 0
            ? String(Int(rounded))
            : String(format: "%.1f", rounded)
    }

    private func formatSignedMs(_ value: Double) -> String {
        let formatted = formatMs(value)
        if value > 0.5 { return "+\(formatted)" }
        if value < -0.5 { return "−\(formatted)" }
        return "0"
    }
}
