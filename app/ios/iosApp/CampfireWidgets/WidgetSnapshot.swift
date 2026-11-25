/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
import Foundation
import WidgetKit

/// What the app writes for the widgets (`:presentation`'s `WidgetSnapshot`, the same keys), in the App Group's
/// container: the extension links no Kotlin, and never reads the library itself. Keys it does not know are ignored, as
/// the Kotlin side ignores them too.
struct WidgetSnapshot: Decodable {
    let songs: [Song]
    let setlists: [Setlist]
    let areSongsEnabled: Bool
    let areSetlistsEnabled: Bool
    let homeLink: String
    let setlistsLink: String
    let strings: Strings
    /// Absent from a snapshot an app from before it wrote, which leaves the widget in the system's colors.
    let theme: Theme?
    /// Every tag of the library, for a Random picks widget's filter; absent from a snapshot an app from before it wrote.
    let tags: [String]?
    /// Every language of the library (`und` for the songs naming none), for the same; absent likewise.
    let languages: [Language]?

    struct Song: Decodable {
        let fileName: String
        let title: String
        let artist: String
        let link: String
        /// Its tags as the file writes them; absent from a snapshot an app from before it wrote.
        let tags: [String]?
        /// Its languages as lowercase ISO codes, empty where it names none; absent likewise.
        let languages: [String]?
    }

    /// A language of the library by its code, and its name in the app's language.
    struct Language: Decodable {
        let code: String
        let name: String
    }

    struct Setlist: Decodable {
        let fileName: String
        /// Its day as `yyyy-MM-dd`.
        let date: String
        let title: String
        let isArchived: Bool
        let songFileNames: [String]
        let link: String
        /// Each song's link, opening the setlist at it; absent from a snapshot an app from before it wrote.
        let songLinks: [String]?
    }

    /// The app's theme: its dark half (true), its light half (false), or the system's (nil); and its palette's two
    /// halves, absent where the app takes the system's own colors.
    struct Theme: Decodable {
        let isDark: Bool?
        let light: Palette?
        let dark: Palette?
    }

    /// The few colors a widget is drawn in, each as `0xAARRGGBB`.
    struct Palette: Decodable {
        let background: UInt32
        let accent: UInt32
        let text: UInt32
        let secondaryText: UInt32
    }

    /// Every text the widgets show, in the app's language rather than the system's.
    struct Strings: Decodable {
        let isRightToLeft: Bool
        let randomPicks: String
        let drawNewPicks: String
        let emptyLibrary: String
        let songsOff: String
        let setlistsOff: String
        let nextGig: String
        let noUpcomingSetlist: String
        let setlistMissing: String
        let today: String
        let tomorrow: String
        let yesterday: String
        let inDays: Plural
        let daysAgo: Plural
        /// Absent from a snapshot an app from before it wrote, which says the library is empty instead.
        let noMatchingSongs: String?
    }

    /// A counted text as the app's language reads each count, so that no plural rule is needed here: past the list, a
    /// count reads as the one with the same last two digits past 100.
    struct Plural: Decodable {
        let byCount: [String]

        func format(_ count: Int) -> String {
            let index = count < byCount.count ? count : 100 + count % 100
            let template = byCount.indices.contains(max(index, 0)) ? byCount[max(index, 0)] : (byCount.last ?? "")
            return template.replacingOccurrences(of: "%1$d", with: String(count))
        }
    }

    /// The snapshot the app last wrote, or nil before it ever has (or where the file is not one).
    static func load() -> WidgetSnapshot? {
        guard let url = AppGroup.containerURL?.appendingPathComponent("Widget/snapshot.json"),
              let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }

    /// A timeline's end when `load()` found nothing: soon, since the app reloads the widgets only when it writes a snapshot
    /// that differs from the file, and a file the widget could not read would otherwise leave it saying so for a day.
    static var retryPolicy: TimelineReloadPolicy { .after(Date().addingTimeInterval(15 * 60)) }
}

/// The App Group the app shares with its extensions, named by `CampfireAppGroup` in the `Info.plist`.
enum AppGroup {
    static let identifier = Bundle.main.object(forInfoDictionaryKey: "CampfireAppGroup") as? String

    static var containerURL: URL? {
        identifier.flatMap { FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: $0) }
    }

    static var defaults: UserDefaults? { identifier.flatMap { UserDefaults(suiteName: $0) } }
}

/// The address a tap opens, or the app's own scheme alone where one is not a URL (which opens the home screen).
func appLink(_ link: String) -> URL {
    URL(string: link) ?? URL(string: "campfire://")!
}
