import SwiftUI
import shared

struct PresetsView: View {
    @EnvironmentObject private var vm: MetronomeViewModel
    let onOpenMetronome: () -> Void

    @State private var showSaveSheet = false
    @State private var deleteCandidate: MetronomePreset?

    var body: some View {
        ZStack {
            ThaaLamTheme.background.ignoresSafeArea()
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    header
                    currentSetupCard
                    Text("Saved · \(vm.presets.count)")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(ThaaLamTheme.textSecondary)
                    if vm.presets.isEmpty {
                        SectionCard {
                            Text("No presets yet")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundStyle(ThaaLamTheme.textPrimary)
                            Text("Save your first setup above. Presets stay on this device.")
                                .font(.system(size: 12))
                                .foregroundStyle(ThaaLamTheme.textSecondary)
                        }
                    }
                    ForEach(vm.presets, id: \.id) { preset in
                        PresetCardView(
                            preset: preset,
                            onLoad: {
                                vm.applyPreset(preset)
                                onOpenMetronome()
                            },
                            onDelete: { deleteCandidate = preset }
                        )
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 100)
            }
        }
        .sheet(isPresented: $showSaveSheet) {
            SavePresetSheet(suggested: "\(formatBpmPreset(vm.settings.bpm)) BPM · \(vm.settings.meter.label)") { name in
                vm.savePreset(name: name)
                showSaveSheet = false
            }
        }
        .alert(
            "Delete preset?",
            isPresented: Binding(
                get: { deleteCandidate != nil },
                set: { if !$0 { deleteCandidate = nil } }
            )
        ) {
            Button("Delete", role: .destructive) {
                if let preset = deleteCandidate {
                    vm.deletePreset(preset)
                }
                deleteCandidate = nil
            }
            Button("Cancel", role: .cancel) { deleteCandidate = nil }
        } message: {
            Text("\"\(deleteCandidate?.name ?? "")\" will be removed from this device.")
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("Presets")
                .font(.system(size: 28, weight: .black))
                .foregroundStyle(ThaaLamTheme.textPrimary)
                .accessibilityAddTraits(.isHeader)
            Text("Save a tempo, meter, groove and practice setup for the next session.")
                .font(.system(size: 12))
                .foregroundStyle(ThaaLamTheme.textSecondary)
        }
    }

    private var currentSetupCard: some View {
        SectionCard(title: "Current setup") {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .lastTextBaseline, spacing: 6) {
                    Text(formatBpmPreset(vm.settings.bpm))
                        .font(.system(size: 42, weight: .bold))
                        .foregroundStyle(ThaaLamTheme.saffron)
                    Text("BPM")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(ThaaLamTheme.textSecondary)
                }
                Text(currentSummary)
                    .font(.system(size: 13))
                    .foregroundStyle(ThaaLamTheme.textSecondary)
                Button("Save current setup") { showSaveSheet = true }
                    .buttonStyle(PrimaryButtonStyle())
            }
        }
    }

    private var currentSummary: String {
        var parts = [vm.settings.meter.label, vm.settings.subdivision.shortLabel]
        if vm.settings.swingPercent > 50.5 {
            parts.append("\(Int(vm.settings.swingPercent)) % swing")
        }
        if let tala = vm.currentTala { parts.append(tala.name) }
        return parts.joined(separator: " · ")
    }
}

private struct PresetCardView: View {
    let preset: MetronomePreset
    let onLoad: () -> Void
    let onDelete: () -> Void

    private var tala: Tala? {
        TalaLibrary.shared.find(id: preset.settings.talaId)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(preset.name)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(ThaaLamTheme.textPrimary)
                    Text("\(preset.bpmLabel) BPM · \(preset.settings.meter.label) · \(preset.settings.subdivision.shortLabel)")
                        .font(.system(size: 12))
                        .foregroundStyle(ThaaLamTheme.textSecondary)
                    if let tala {
                        Text(tala.name)
                            .font(.system(size: 12))
                            .foregroundStyle(ThaaLamTheme.saffron)
                    }
                }
                Spacer()
                Button(action: onDelete) {
                    Image(systemName: "trash")
                        .foregroundStyle(ThaaLamTheme.textSecondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Delete \(preset.name)")
            }
            Button(action: onLoad) {
                Label("Load", systemImage: "play.fill")
                    .font(.system(size: 14, weight: .semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(ThaaLamTheme.surfaceVariant)
                    .foregroundStyle(ThaaLamTheme.textPrimary)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(ThaaLamTheme.surface, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .strokeBorder(ThaaLamTheme.border.opacity(0.7), lineWidth: 1)
        )
    }
}

private struct SavePresetSheet: View {
    @Environment(\.dismiss) private var dismiss
    let suggested: String
    let onSave: (String) -> Void
    @State private var name: String

    init(suggested: String, onSave: @escaping (String) -> Void) {
        self.suggested = suggested
        self.onSave = onSave
        _name = State(initialValue: suggested)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Name") {
                    TextField("Preset name", text: $name)
                }
            }
            .navigationTitle("Save preset")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        onSave(name)
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
        .presentationDetents([.height(220)])
    }
}

private func formatBpmPreset(_ bpm: Double) -> String {
    let rounded = (bpm * 10).rounded() / 10
    return rounded.truncatingRemainder(dividingBy: 1) == 0
        ? String(Int(rounded))
        : String(format: "%.1f", rounded)
}
