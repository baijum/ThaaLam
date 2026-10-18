import SwiftUI
import UIKit

@main
struct ThaaLamApp: App {
    @StateObject private var model = MetronomeViewModel()

    init() {
        let tabBar = UITabBarAppearance()
        tabBar.configureWithDefaultBackground()
        UITabBar.appearance().standardAppearance = tabBar
        UITabBar.appearance().scrollEdgeAppearance = tabBar

        let navigationBar = UINavigationBarAppearance()
        navigationBar.configureWithDefaultBackground()
        UINavigationBar.appearance().standardAppearance = navigationBar
        UINavigationBar.appearance().scrollEdgeAppearance = navigationBar
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(model)
                .preferredColorScheme(nil)
        }
    }
}
