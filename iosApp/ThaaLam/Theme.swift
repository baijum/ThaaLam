import SwiftUI
import UIKit

/// ThaaLam's palette: warm saffron for the sam, cool teal for the answering
/// pulse, lavender for ghosted (khali) beats and a deep charcoal stage.
enum ThaaLamTheme {
    static let saffron = Color(hex: 0xF2B457)
    static let teal = Color(hex: 0x5FD0C4)
    static let lavender = Color(hex: 0xB69CFF)
    static let coral = Color(hex: 0xFF7A6E)

    static let background = Color.dynamic(light: 0xFBF7F0, dark: 0x0E1116)
    static let surface = Color.dynamic(light: 0xFFFFFF, dark: 0x151B23)
    static let surfaceVariant = Color.dynamic(light: 0xF0E9DD, dark: 0x222B36)
    static let border = Color.dynamic(light: 0xD8D0C2, dark: 0x2F3A46)
    static let textPrimary = Color.dynamic(light: 0x1D1B16, dark: 0xF6EFE3)
    static let textSecondary = Color.dynamic(light: 0x4C463C, dark: 0xB8C2CE)
    static let outline = Color.dynamic(light: 0x807568, dark: 0x536170)
}

extension Color {
    init(hex: UInt32, alpha: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: alpha
        )
    }

    static func dynamic(light: UInt32, dark: UInt32) -> Color {
        Color(uiColor: UIColor { traits in
            let hex = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(
                red: CGFloat((hex >> 16) & 0xFF) / 255.0,
                green: CGFloat((hex >> 8) & 0xFF) / 255.0,
                blue: CGFloat(hex & 0xFF) / 255.0,
                alpha: 1.0
            )
        })
    }
}

extension View {
    /// The rounded, hairline-bordered surface used for every section card.
    func sectionCard(padding: CGFloat = 18) -> some View {
        self
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(ThaaLamTheme.surface, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .strokeBorder(ThaaLamTheme.border.opacity(0.7), lineWidth: 1)
            )
    }
}
