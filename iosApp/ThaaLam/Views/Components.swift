import SwiftUI

/// A minimal wrapping layout used for additive beat groups and chip rows.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    var lineSpacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? 360
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        var widest: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > maxWidth {
                x = 0
                y += rowHeight + lineSpacing
                rowHeight = 0
            }
            x += size.width + spacing
            widest = max(widest, x - spacing)
            rowHeight = max(rowHeight, size.height)
        }
        return CGSize(width: maxWidth, height: y + rowHeight)
    }

    func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > bounds.minX && x + size.width > bounds.maxX {
                x = bounds.minX
                y += rowHeight + lineSpacing
                rowHeight = 0
            }
            subview.place(
                at: CGPoint(x: x, y: y),
                anchor: .topLeading,
                proposal: ProposedViewSize(size)
            )
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

struct SectionCard<Content: View>: View {
    var title: String?
    var subtitle: String?
    var trailing: AnyView?
    private let content: () -> Content

    init(
        title: String? = nil,
        subtitle: String? = nil,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.title = title
        self.subtitle = subtitle
        self.trailing = nil
        self.content = content
    }

    init<Trailing: View>(
        title: String? = nil,
        subtitle: String? = nil,
        @ViewBuilder trailing: @escaping () -> Trailing,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.title = title
        self.subtitle = subtitle
        self.trailing = AnyView(trailing())
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if title != nil || trailing != nil {
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 2) {
                        if let title {
                            Text(title)
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundStyle(ThaaLamTheme.textPrimary)
                                .accessibilityAddTraits(.isHeader)
                        }
                        if let subtitle {
                            Text(subtitle)
                                .font(.system(size: 12))
                                .foregroundStyle(ThaaLamTheme.textSecondary)
                        }
                    }
                    Spacer(minLength: 8)
                    if let trailing { trailing }
                }
            }
            content()
        }
        .sectionCard()
    }
}

struct FieldLabel: View {
    let text: String
    init(_ text: String) { self.text = text }

    var body: some View {
        Text(text)
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(ThaaLamTheme.textSecondary)
    }
}

struct ChipButton: View {
    let label: String
    let selected: Bool
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(selected ? Color(hex: 0x2A1900) : ThaaLamTheme.textPrimary)
                .padding(.horizontal, 13)
                .padding(.vertical, 7)
                .background(
                    Capsule().fill(selected ? ThaaLamTheme.saffron : ThaaLamTheme.surfaceVariant)
                )
                .overlay(
                    Capsule().strokeBorder(
                        selected ? ThaaLamTheme.saffron : ThaaLamTheme.border,
                        lineWidth: 1
                    )
                )
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.4)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

struct StepperRow: View {
    let label: String
    var supportingText: String? = nil
    let value: String
    var enabled = true
    let onDecrease: () -> Void
    let onIncrease: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(.system(size: 15))
                    .foregroundStyle(ThaaLamTheme.textPrimary)
                if let supportingText {
                    Text(supportingText)
                        .font(.system(size: 12))
                        .foregroundStyle(ThaaLamTheme.textSecondary)
                }
            }
            Spacer(minLength: 8)
            Button(action: onDecrease) {
                Image(systemName: "minus.circle")
                    .font(.system(size: 22))
                    .foregroundStyle(ThaaLamTheme.textSecondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Decrease \(label)")
            Text(value)
                .font(.system(size: 16, weight: .semibold))
                .frame(minWidth: 44)
                .multilineTextAlignment(.center)
                .foregroundStyle(ThaaLamTheme.textPrimary)
            Button(action: onIncrease) {
                Image(systemName: "plus.circle")
                    .font(.system(size: 22))
                    .foregroundStyle(ThaaLamTheme.saffron)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Increase \(label)")
        }
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.4)
    }
}

struct ToggleRow: View {
    let title: String
    var subtitle: String? = nil
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 15))
                    .foregroundStyle(ThaaLamTheme.textPrimary)
                if let subtitle {
                    Text(subtitle)
                        .font(.system(size: 12))
                        .foregroundStyle(ThaaLamTheme.textSecondary)
                }
            }
        }
        .tint(ThaaLamTheme.saffron)
    }
}

struct InfoRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            FieldLabel(label)
            Spacer(minLength: 12)
            Text(value)
                .font(.system(size: 13))
                .foregroundStyle(ThaaLamTheme.textSecondary)
                .multilineTextAlignment(.trailing)
        }
    }
}

struct PrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 15, weight: .semibold))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(ThaaLamTheme.saffron.opacity(configuration.isPressed ? 0.75 : 1))
            .foregroundStyle(Color(hex: 0x2A1900))
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

struct SecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 15, weight: .semibold))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(ThaaLamTheme.surfaceVariant.opacity(configuration.isPressed ? 0.55 : 1))
            .foregroundStyle(ThaaLamTheme.textPrimary)
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(ThaaLamTheme.border, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}
