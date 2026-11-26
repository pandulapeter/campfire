/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
import AppIntents
import SwiftUI
import WidgetKit

/// The page a setlist widget last turned to, kept in the group's defaults by what the widget was set to show (a setlist's
/// file name, or Next gig's id) and the songs a page of it holds, so every widget set alike and as large turns together. It remembers which setlist the page was turned
/// in: Next gig moves on to another setlist without anyone turning a page, and that one starts on its first.
struct SetlistPage {
    let setlistFileName: String
    let page: Int

    private static func key(_ identity: String) -> String { "setlistPage.\(identity)" }

    static func stored(for identity: String) -> SetlistPage? {
        guard let stored = AppGroup.defaults?.dictionary(forKey: key(identity)),
              let setlistFileName = stored["setlist"] as? String,
              let page = stored["page"] as? Int
        else { return nil }
        return SetlistPage(setlistFileName: setlistFileName, page: page)
    }

    static func store(_ page: SetlistPage, for identity: String) {
        AppGroup.defaults?.set(["setlist": page.setlistFileName, "page": page.page], forKey: key(identity))
    }

    /// The page to show of `setlistFileName`'s `pageCount`: the stored one where it was turned in that setlist, within the
    /// pages it has now (it may have lost songs since), else the first.
    static func shown(stored: SetlistPage?, setlistFileName: String, pageCount: Int) -> Int {
        guard let stored, stored.setlistFileName == setlistFileName else { return 0 }
        return min(max(stored.page, 0), max(pageCount - 1, 0))
    }
}

/// Turns a setlist widget to another page of its songs.
struct TurnSetlistPageIntent: AppIntent {
    static var title: LocalizedStringResource = "Setlist"
    static var isDiscoverable = false

    /// What the widget was set to show (`SetlistEntity`'s id).
    @Parameter(title: "Setlist") var identity: String
    /// The file name of the setlist it shows now.
    @Parameter(title: "Setlist") var setlistFileName: String
    @Parameter(title: "Setlist") var page: Int

    init() {}

    init(identity: String, setlistFileName: String, page: Int) {
        self.identity = identity
        self.setlistFileName = setlistFileName
        self.page = page
    }

    func perform() async throws -> some IntentResult {
        SetlistPage.store(SetlistPage(setlistFileName: setlistFileName, page: page), for: identity)
        // Only the widget pressed is reloaded by itself; the others set to the same setlist share its page.
        WidgetCenter.shared.reloadTimelines(ofKind: SetlistWidget.kind)
        return .result()
    }
}

/// Previous and next page, drawn as Random picks draws its Shuffle button. The chevrons are the direction-aware symbols,
/// so in a right-to-left language (the widget takes the app's direction) Previous points right and stands on the right.
/// The one with no page to go to is dimmed rather than hidden, so the other never moves, and stays a button that turns to
/// the page already shown: a disabled button in a widget passes its tap through to the widget's link, opening the setlist.
struct SetlistPageButtons: View {
    let identity: String
    let setlistFileName: String
    let page: Int
    let pageCount: Int
    /// The snapshot's, for the buttons' spoken names in the app's language.
    let strings: WidgetSnapshot.Strings?
    @Environment(\.widgetColors) private var colors

    var body: some View {
        HStack(spacing: 12) {
            button(symbol: "chevron.backward", label: strings?.previousPage, target: page - 1)
            button(symbol: "chevron.forward", label: strings?.nextPage, target: page + 1)
        }
    }

    private func button(symbol: String, label: String?, target: Int) -> some View {
        let isEnabled = (0..<pageCount).contains(target)
        return Button(intent: TurnSetlistPageIntent(identity: identity, setlistFileName: setlistFileName, page: isEnabled ? target : page)) {
            Image(systemName: symbol)
                .font(.system(size: 15, weight: .bold))
                .frame(width: 20, height: 20)
                .foregroundStyle(colors.accent)
                .widgetAccentable()
                .opacity(isEnabled ? 1 : 0.35)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(verbatim: label ?? ""))
    }
}
