/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
import SwiftUI
import WidgetKit

/// The app's Inter (the extension's `UIAppFonts`, the same files the app bundles), by PostScript name: the medium cut
/// is a family of its own, so a weight asked of "Inter" would not find it. Each size follows Dynamic Type.
enum InterFont {
    static func regular(_ size: CGFloat, relativeTo style: Font.TextStyle = .body) -> Font {
        .custom("Inter-Regular", size: size, relativeTo: style)
    }

    static func medium(_ size: CGFloat, relativeTo style: Font.TextStyle = .body) -> Font {
        .custom("Inter-Medium", size: size, relativeTo: style)
    }

    static func bold(_ size: CGFloat, relativeTo style: Font.TextStyle = .headline) -> Font {
        .custom("Inter-Bold", size: size, relativeTo: style)
    }
}

extension Color {
    /// The app's brand purple, lighter in the dark (the extension's `AccentColor`).
    static let campfireAccent = Color("AccentColor")
}

/// The colors a widget's views are drawn in: the app's palette where the snapshot has one (`WidgetSnapshot.Theme`), else
/// the system's, which is also what a widget drawn before the app wrote a snapshot takes.
struct WidgetColors {
    var accent = Color.campfireAccent
    var text = Color.primary
    var secondaryText = Color.secondary
    /// Nil for the system's widget background.
    var background: Color?

    init() {}

    /// [theme]'s palette, in its fixed half, or in whichever half the system is in.
    init(theme: WidgetSnapshot.Theme?) {
        guard let light = theme?.light, let dark = theme?.dark else { return }
        func color(_ role: (WidgetSnapshot.Palette) -> UInt32) -> Color {
            switch theme?.isDark {
            case .some(true): return Color(argb: role(dark))
            case .some(false): return Color(argb: role(light))
            case .none:
                let (day, night) = (role(light), role(dark))
                return Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(argb: night) : UIColor(argb: day) })
            }
        }
        accent = color(\.accent)
        text = color(\.text)
        secondaryText = color(\.secondaryText)
        background = color(\.background)
    }
}

private struct WidgetColorsKey: EnvironmentKey {
    static let defaultValue = WidgetColors()
}

extension EnvironmentValues {
    var widgetColors: WidgetColors {
        get { self[WidgetColorsKey.self] }
        set { self[WidgetColorsKey.self] = newValue }
    }
}

extension UIColor {
    convenience init(argb: UInt32) {
        self.init(
            red: CGFloat((argb >> 16) & 0xFF) / 255,
            green: CGFloat((argb >> 8) & 0xFF) / 255,
            blue: CGFloat(argb & 0xFF) / 255,
            alpha: CGFloat((argb >> 24) & 0xFF) / 255
        )
    }
}

extension Color {
    init(argb: UInt32) { self.init(UIColor(argb: argb)) }
}

extension View {
    /// The app's theme ([WidgetColors], and its light or dark half where it is set to one), and the layout direction of
    /// the app's language rather than the system's. The text grows with the reader's size only up to the largest a
    /// widget's fixed height has room for: its rows are counted by its family, not by its text.
    func campfireWidget(strings: WidgetSnapshot.Strings, theme: WidgetSnapshot.Theme?) -> some View {
        let colors = WidgetColors(theme: theme)
        return environment(\.layoutDirection, strings.isRightToLeft ? .rightToLeft : .leftToRight)
            .environment(\.widgetColors, colors)
            .foregroundStyle(colors.text)
            .dynamicTypeSize(...DynamicTypeSize.large)
            .containerBackground(colors.background.map(AnyShapeStyle.init) ?? AnyShapeStyle(.background), for: .widget)
            .transformEnvironment(\.colorScheme) { scheme in
                if let isDark = theme?.isDark { scheme = isDark ? .dark : .light }
            }
    }
}

/// A sentence in place of the widget's content, centered.
struct WidgetMessage: View {
    let text: String
    @Environment(\.widgetColors) private var colors

    var body: some View {
        Text(verbatim: text)
            .font(InterFont.medium(15))
            .multilineTextAlignment(.center)
            .foregroundStyle(colors.secondaryText)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// What a widget says before the app has ever written its snapshot: the extension's own string, in the system's
/// language, since the app's choice of language is in the snapshot too.
struct NoSnapshotMessage: View {
    var body: some View {
        Text("Open Campfire to fill this widget.")
            .font(InterFont.medium(15))
            .multilineTextAlignment(.center)
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
