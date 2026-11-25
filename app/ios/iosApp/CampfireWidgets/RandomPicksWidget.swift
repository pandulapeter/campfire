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

/// A few songs drawn from the library, or from the tags and languages Edit Widget chose, drawn again every hour and by
/// the Shuffle button. Its own draw, not the songs list's random picks: neither ever changes the other's.
struct RandomPicksWidget: Widget {
    nonisolated static let kind = "RandomPicks"

    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: Self.kind, intent: RandomPicksConfigurationIntent.self, provider: RandomPicksProvider()) { entry in
            RandomPicksView(entry: entry)
        }
        .configurationDisplayName("Random picks")
        .description("A few random songs from your library, new ones every hour.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
        .contentMarginsDisabled()
    }
}

struct RandomPicksEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot?
    /// What this entry's draw is made by: the seed Shuffle last stored, mixed with the hour.
    let seed: UInt64
    /// The header's title from Edit Widget, nil for the snapshot's.
    let title: String?
    let filter: RandomPicksFilter
    /// How many songs it draws, from Edit Widget; the widget shows as many of them as its size has room for.
    let count: Int
}

/// A day of hourly entries, each its own draw, and a new timeline once they are used up. The seed of an hour is the
/// stored seed mixed with the hour's number, so a timeline asked for again within the hour (a snapshot written) draws
/// the same songs from the same library.
struct RandomPicksProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> RandomPicksEntry {
        RandomPicksEntry(
            date: Date(), snapshot: nil, seed: 0, title: nil, filter: RandomPicksFilter(), count: RandomPicksConfigurationIntent.defaultCount
        )
    }

    func snapshot(for configuration: RandomPicksConfigurationIntent, in context: Context) async -> RandomPicksEntry {
        entry(at: Date(), configuration: configuration, snapshot: WidgetSnapshot.load())
    }

    func timeline(for configuration: RandomPicksConfigurationIntent, in context: Context) async -> Timeline<RandomPicksEntry> {
        let snapshot = WidgetSnapshot.load()
        let now = Date()
        let hour = floor(now.timeIntervalSince1970 / 3600) * 3600
        let entries = [entry(at: now, configuration: configuration, snapshot: snapshot)] + (1...24).map { offset in
            entry(at: Date(timeIntervalSince1970: hour + Double(offset) * 3600), configuration: configuration, snapshot: snapshot)
        }
        return Timeline(entries: entries, policy: snapshot == nil ? WidgetSnapshot.retryPolicy : .atEnd)
    }

    private func entry(at date: Date, configuration: RandomPicksConfigurationIntent, snapshot: WidgetSnapshot?) -> RandomPicksEntry {
        let hour = UInt64(max(0, Int64(date.timeIntervalSince1970 / 3600)))
        let title = configuration.title?.trimmingCharacters(in: .whitespacesAndNewlines)
        // Mixed, not added: a seed one increment of the generator's own on would start the same stream one value later,
        // so each hour would show nearly the picks of the hour before it.
        return RandomPicksEntry(
            date: date,
            snapshot: snapshot,
            seed: SplitMix.mix(ShuffleSeed.current ^ hour),
            title: title?.isEmpty == false ? title : nil,
            filter: configuration.filter,
            count: configuration.pickCount
        )
    }
}

/// The seed Shuffle draws a new one of, in the group's defaults so that every timeline built after it sees it.
enum ShuffleSeed {
    private static let key = "randomPicksSeed"

    static var current: UInt64 {
        UInt64(bitPattern: Int64(AppGroup.defaults?.object(forKey: key) as? Int ?? 0))
    }

    static func renew() {
        AppGroup.defaults?.set(Int.random(in: Int.min...Int.max), forKey: key)
    }
}

/// Draws new picks at once, in every Random picks widget.
struct ShuffleIntent: AppIntent {
    static var title: LocalizedStringResource = "Draw new picks"
    static var isDiscoverable = false

    func perform() async throws -> some IntentResult {
        ShuffleSeed.renew()
        WidgetCenter.shared.reloadTimelines(ofKind: RandomPicksWidget.kind)
        return .result()
    }
}

/// SplitMix64's finalizer: nearby inputs give unrelated outputs.
enum SplitMix {
    static func mix(_ value: UInt64) -> UInt64 {
        var z = value
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}

struct RandomPicksView: View {
    let entry: RandomPicksEntry
    @Environment(\.widgetFamily) private var family
    @Environment(\.widgetContentMargins) private var margins

    /// Its own, since the theme is put in the environment of the view it draws rather than its own.
    private var colors: WidgetColors { WidgetColors(theme: entry.snapshot?.theme) }

    var body: some View {
        if let snapshot = entry.snapshot {
            content(snapshot)
                .widgetURL(appLink(snapshot.homeLink))
                .campfireWidget(strings: snapshot.strings, theme: snapshot.theme)
        } else {
            NoSnapshotMessage().containerBackground(.background, for: .widget)
        }
    }

    @ViewBuilder
    private func content(_ snapshot: WidgetSnapshot) -> some View {
        let songs = entry.filter.songs(of: snapshot)
        VStack(alignment: .leading, spacing: 4) {
            // Only where there are more to draw from than it draws, so it never only reorders the same songs, and not on
            // small, whose width the title needs (the title shortened to make room would no longer say what it is).
            header(snapshot.strings, canDrawAgain: family != .systemSmall && snapshot.areSongsEnabled && songs.count > entry.count)
                .padding(.leading, margins.leading)
                .padding(.trailing, margins.trailing)
            if !snapshot.areSongsEnabled {
                WidgetMessage(text: snapshot.strings.songsOff)
            } else if snapshot.songs.isEmpty {
                WidgetMessage(text: snapshot.strings.emptyLibrary)
            } else if songs.isEmpty {
                WidgetMessage(text: snapshot.strings.noMatchingSongs.flatMap { $0.isEmpty ? nil : $0 } ?? snapshot.strings.emptyLibrary)
            } else {
                // The draw of the whole count, cut to the rows that fit: a smaller widget shows the first of the same picks.
                let capacity = family.songRowCapacity
                WidgetSongRows(count: capacity, rows: draw(songs, count: min(entry.count, capacity)).map { song in
                    WidgetSongRow(title: song.title, artist: song.artist, cover: song.cover, link: song.link)
                })
            }
        }
        .padding(.top, margins.top)
        .padding(.bottom, margins.bottom)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    /// Up to `count` songs, by this entry's seed: every song can be drawn, however few there are. Those whose file names,
    /// mixed with the seed, rank first, as `:presentation`'s `pickWidgetSongs` draws them: a song's rank is its own name's
    /// alone, so the library's order leaves the picks as they are, and a song added changes one of them at most.
    private func draw(_ songs: [WidgetSnapshot.Song], count: Int) -> [WidgetSnapshot.Song] {
        Array(songs.map { (rank: Self.rank(of: $0.fileName, seed: entry.seed), song: $0) }.sorted { $0.rank < $1.rank }.prefix(count).map(\.song))
    }

    /// The file name's 64-bit FNV-1a hash, mixed with `seed`.
    private static func rank(of fileName: String, seed: UInt64) -> UInt64 {
        let hash = fileName.utf8.reduce(UInt64(0xCBF2_9CE4_8422_2325)) { ($0 ^ UInt64($1)) &* 0x0000_0100_0000_01B3 }
        return SplitMix.mix(hash ^ seed)
    }

    private func header(_ strings: WidgetSnapshot.Strings, canDrawAgain: Bool) -> some View {
        HStack {
            Text(verbatim: entry.title ?? strings.randomPicks)
                .font(InterFont.bold(15))
                .foregroundStyle(colors.accent)
                .widgetAccentable()
                .lineLimit(1)
            Spacer()
            if canDrawAgain {
                Button(intent: ShuffleIntent()) {
                    // The app's dice (`ic_widget_shuffle.xml`'s path), whose 24-unit box keeps a margin around the glyph,
                    // so the box is larger than the symbol it replaces for the dice to look as large.
                    Image("Shuffle")
                        .renderingMode(.template)
                        .resizable()
                        .frame(width: 20, height: 20)
                        .foregroundStyle(colors.accent)
                        .widgetAccentable()
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(verbatim: strings.drawNewPicks))
            }
        }
    }
}
