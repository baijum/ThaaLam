import SwiftUI
import shared

struct SettingsView: View {
    @EnvironmentObject private var vm: MetronomeViewModel
    @State private var confirmReset = false

    private var version: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.1.0"
    }

    var body: some View {
        ZStack {
            ThaaLamTheme.background.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header

                    SectionCard(
                        title: "Feel",
                        subtitle: "How ThaaLam responds while you play"
                    ) {
                        VStack(alignment: .leading, spacing: 10) {
                            ToggleRow(
                                title: "Haptic pulse",
                                subtitle: "Feel the downbeat even in silent practice",
                                isOn: Binding(
                                    get: { vm.uiPreferences.hapticsEnabled },
                                    set: { vm.setHapticsEnabled($0) }
                                )
                            )
                            ToggleRow(
                                title: "Screen flash",
                                subtitle: "Brief flash on every main pulse",
                                isOn: Binding(
                                    get: { vm.uiPreferences.flashEnabled },
                                    set: { vm.setFlashEnabled($0) }
                                )
                            )
                            ToggleRow(
                                title: "Keep screen awake",
                                subtitle: "Prevent the display from sleeping during practice",
                                isOn: Binding(
                                    get: { vm.uiPreferences.keepScreenOn },
                                    set: { vm.setKeepScreenOn($0) }
                                )
                            )
                        }
                    }

                    SectionCard(
                        title: "Display calibration",
                        subtitle: "Bluetooth headphones can delay sound by 80–250 ms",
                        trailing: {
                            Text("\(vm.uiPreferences.visualLatencyMs) ms")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(ThaaLamTheme.saffron)
                        }
                    ) {
                        VStack(alignment: .leading, spacing: 8) {
                            Slider(
                                value: Binding(
                                    get: { Double(vm.uiPreferences.visualLatencyMs) },
                                    set: { vm.setVisualLatency(Int(($0 / 10).rounded()) * 10) }
                                ),
                                in: 0...300,
                                step: 10
                            )
                            .tint(ThaaLamTheme.saffron)
                            Text("Delays the visual pulse so it lands with the click you actually hear. Leave at 0 ms for wired headphones and the built-in speaker.")
                                .font(.system(size: 12))
                                .foregroundStyle(ThaaLamTheme.textSecondary)
                        }
                    }

                    SectionCard(title: "Current engine") {
                        VStack(alignment: .leading, spacing: 8) {
                            InfoRow(label: "Tempo", value: "\(Int(vm.settings.bpm.rounded())) BPM")
                            InfoRow(label: "Meter", value: "\(vm.settings.meter.label) · \(vm.settings.meter.groupingLabel)")
                            InfoRow(label: "Subdivision", value: vm.settings.subdivision.displayName)
                            InfoRow(label: "Sound", value: vm.settings.soundSet.displayName)
                            InfoRow(label: "Count-in", value: "\(vm.settings.countInBars) bar(s)")
                            if let tala = vm.currentTala {
                                InfoRow(label: "Tala", value: tala.name)
                            }
                            Button("Reset metronome settings") {
                                confirmReset = true
                            }
                            .buttonStyle(SecondaryButtonStyle())
                        }
                    }

                    SectionCard(title: "About ThaaLam") {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("A precise, offline rhythm companion for practice rooms and stages — with first-class Carnatic and Hindustani tala support.")
                                .font(.system(size: 14))
                                .foregroundStyle(ThaaLamTheme.textPrimary)
                            InfoRow(label: "Version", value: version)
                            InfoRow(label: "Platform", value: "Kotlin Multiplatform · SwiftUI")
                            InfoRow(label: "Audio", value: "48 kHz sample-accurate synthesis")
                            InfoRow(label: "Privacy", value: "No accounts, ads, analytics or network access")
                            Text("Everything you save stays on this device. ThaaLam works with the radio off.")
                                .font(.system(size: 12))
                                .foregroundStyle(ThaaLamTheme.textSecondary)
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 100)
            }
        }
        .alert("Reset settings?", isPresented: $confirmReset) {
            Button("Reset", role: .destructive) { vm.resetSettings() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Tempo, meter, accent pattern, practice options and sound will return to their defaults. Saved presets are kept.")
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("Settings")
                .font(.system(size: 28, weight: .black))
                .foregroundStyle(ThaaLamTheme.textPrimary)
                .accessibilityAddTraits(.isHeader)
            Text("Feel, feedback and the honest details.")
                .font(.system(size: 12))
                .foregroundStyle(ThaaLamTheme.textSecondary)
        }
    }
}
