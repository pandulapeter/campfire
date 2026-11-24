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

/// A few songs drawn from the library, drawn again every hour and by the Shuffle button. Its own draw, not the songs
/// list's random picks: neither ever changes the other's.
struct RandomPicksWidget: Widget {
    nonisolated static let kind = "RandomPicks"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: RandomPicksProvider()) { entry in
            RandomPicksView(entry: entry)
        }
        .configurationDisplayName("Random picks")
        .description("A few random songs from your library, new ones every hour.")
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

struct RandomPicksEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot?
    /// What this entry's draw is made by: the seed Shuffle last stored, mixed with the hour.
    let seed: UInt64
}

/// A day of hourly entries, each its own draw, and a new timeline once they are used up. The seed of an hour is the
/// stored seed mixed with the hour's number, so a timeline asked for again within the hour (a snapshot written) draws
/// the same songs from the same library.
struct RandomPicksProvider: TimelineProvider {
    func placeholder(in context: Context) -> RandomPicksEntry {
        RandomPicksEntry(date: Date(), snapshot: nil, seed: 0)
    }

    func getSnapshot(in context: Context, completion: @escaping (RandomPicksEntry) -> Void) {
        completion(entry(at: Date(), snapshot: WidgetSnapshot.load()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<RandomPicksEntry>) -> Void) {
        let snapshot = WidgetSnapshot.load()
        let now = Date()
        let hour = floor(now.timeIntervalSince1970 / 3600) * 3600
        let entries = [entry(at: now, snapshot: snapshot)] + (1...24).map { offset in
            entry(at: Date(timeIntervalSince1970: hour + Double(offset) * 3600), snapshot: snapshot)
        }
        completion(Timeline(entries: entries, policy: snapshot == nil ? WidgetSnapshot.retryPolicy : .atEnd))
    }

    private func entry(at date: Date, snapshot: WidgetSnapshot?) -> RandomPicksEntry {
        let hour = UInt64(max(0, Int64(date.timeIntervalSince1970 / 3600)))
        // Mixed, not added: a seed one increment of the generator's own on would start the same stream one value later,
        // so each hour would show nearly the picks of the hour before it.
        return RandomPicksEntry(date: date, snapshot: snapshot, seed: SplitMix.mix(ShuffleSeed.current ^ hour))
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

    var body: some View {
        if let snapshot = entry.snapshot {
            content(snapshot)
                .widgetURL(appLink(snapshot.homeLink))
                .campfireWidget(isRightToLeft: snapshot.strings.isRightToLeft)
        } else {
            NoSnapshotMessage().containerBackground(.background, for: .widget)
        }
    }

    @ViewBuilder
    private func content(_ snapshot: WidgetSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            header(snapshot.strings)
            if !snapshot.areSongsEnabled {
                WidgetMessage(text: snapshot.strings.songsOff)
            } else if snapshot.songs.isEmpty {
                WidgetMessage(text: snapshot.strings.emptyLibrary)
            } else {
                let picks = draw(snapshot.songs, count: family == .systemLarge ? 5 : 3)
                VStack(alignment: .leading, spacing: family == .systemLarge ? 10 : 6) {
                    ForEach(Array(picks.enumerated()), id: \.offset) { _, song in
                        SongRow(song: song)
                    }
                }
                Spacer(minLength: 0)
            }
        }
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

    private func header(_ strings: WidgetSnapshot.Strings) -> some View {
        HStack {
            Text(verbatim: strings.randomPicks)
                .font(InterFont.bold(15))
                .foregroundStyle(Color.campfireAccent)
                .widgetAccentable()
            Spacer()
            if entry.snapshot?.areSongsEnabled == true, entry.snapshot?.songs.isEmpty == false {
                Button(intent: ShuffleIntent()) {
                    Image(systemName: "shuffle")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.campfireAccent)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(verbatim: strings.drawNewPicks))
            }
        }
    }
}

/// A song's title and artist, opening its details screen.
private struct SongRow: View {
    let song: WidgetSnapshot.Song

    var body: some View {
        Link(destination: appLink(song.link)) {
            VStack(alignment: .leading, spacing: 1) {
                Text(verbatim: song.title)
                    .font(InterFont.medium(14))
                    .lineLimit(1)
                if !song.artist.isEmpty {
                    Text(verbatim: song.artist)
                        .font(InterFont.regular(12, relativeTo: .caption))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}
