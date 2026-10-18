import SwiftUI
import UIKit

struct ContentView: View {
    @EnvironmentObject private var vm: MetronomeViewModel
    @State private var tab: Int

    init() {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        let initial: Int
        if args.contains("-talas") {
            initial = 1
        } else if args.contains("-practice") {
            initial = 2
        } else if args.contains("-presets") {
            initial = 3
        } else if args.contains("-settings") {
            initial = 4
        } else {
            initial = 0
        }
        _tab = State(initialValue: initial)
        #else
        _tab = State(initialValue: 0)
        #endif
    }

    var body: some View {
        TabView(selection: $tab) {
            MetronomeView()
                .tabItem { Label("Metronome", systemImage: "metronome") }
                .tag(0)
            TalasView(onOpenMetronome: { tab = 0 })
                .tabItem { Label("Talas", systemImage: "music.note.list") }
                .tag(1)
            PracticeView()
                .tabItem { Label("Practice", systemImage: "chart.bar.xaxis") }
                .tag(2)
            PresetsView(onOpenMetronome: { tab = 0 })
                .tabItem { Label("Presets", systemImage: "bookmark") }
                .tag(3)
            SettingsView()
                .tabItem { Label("Settings", systemImage: "gearshape") }
                .tag(4)
        }
        .tint(ThaaLamTheme.saffron)
        .onAppear {
            UIApplication.shared.isIdleTimerDisabled = vm.uiPreferences.keepScreenOn
        }
        .onChange(of: vm.uiPreferences.keepScreenOn) { value in
            UIApplication.shared.isIdleTimerDisabled = value
        }
        .alert(
            "Audio problem",
            isPresented: Binding(
                get: { vm.errorMessage != nil },
                set: { if !$0 { vm.errorMessage = nil } }
            )
        ) {
            Button("OK") { vm.errorMessage = nil }
        } message: {
            Text(vm.errorMessage ?? "")
        }
    }
}
