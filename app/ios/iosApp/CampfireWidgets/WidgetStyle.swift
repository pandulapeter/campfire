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

extension View {
    /// The system's widget background, and the layout direction of the app's language rather than the system's.
    func campfireWidget(isRightToLeft: Bool) -> some View {
        environment(\.layoutDirection, isRightToLeft ? .rightToLeft : .leftToRight)
            .containerBackground(.background, for: .widget)
    }
}

/// A sentence in place of the widget's content, centered.
struct WidgetMessage: View {
    let text: String

    var body: some View {
        Text(verbatim: text)
            .font(InterFont.medium(15))
            .multilineTextAlignment(.center)
            .foregroundStyle(.secondary)
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
