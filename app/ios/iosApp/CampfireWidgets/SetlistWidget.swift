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

/// The next gig, or a setlist the user chose, and how far away its day is, over its songs listed as Random picks lists its
/// picks (medium and large). A song opens the setlist at it; the rest of the widget, the setlist's first song read from it
/// (or the setlists screen, where there is no setlist to show).
struct SetlistWidget: Widget {
    nonisolated static let kind = "Setlist"

    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: Self.kind, intent: SetlistConfigurationIntent.self, provider: SetlistProvider()) { entry in
            SetlistWidgetView(entry: entry)
        }
        .configurationDisplayName("Setlist")
        .description("Your next gig, or a setlist you choose, and how many days are left.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .accessoryRectangular])
        .contentMarginsDisabled()
    }
}

struct SetlistEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot?
    /// The file name of the setlist chosen, nil for Next gig.
    let pinnedFileName: String?
    /// What the widget was set to show (`SetlistEntity`'s id) and how many songs a page of it holds, which its page is
    /// kept by: a page number means other songs in a widget of another size.
    let identity: String
    let storedPage: SetlistPage?
}

/// An entry now and one at each of the week's next midnights: "In 3 days" becomes "In 2 days", and a next gig whose day
/// has passed gives way to the one after it.
struct SetlistProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> SetlistEntry {
        SetlistEntry(date: Date(), snapshot: nil, pinnedFileName: nil, identity: SetlistEntity.nextGigId, storedPage: nil)
    }

    func snapshot(for configuration: SetlistConfigurationIntent, in context: Context) async -> SetlistEntry {
        entry(at: Date(), configuration: configuration, family: context.family, snapshot: WidgetSnapshot.load())
    }

    func timeline(for configuration: SetlistConfigurationIntent, in context: Context) async -> Timeline<SetlistEntry> {
        let snapshot = WidgetSnapshot.load()
        let now = Date()
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: now)
        let midnights = (1...7).compactMap { calendar.date(byAdding: .day, value: $0, to: today) }
        let entries = ([now] + midnights).map { entry(at: $0, configuration: configuration, family: context.family, snapshot: snapshot) }
        return Timeline(entries: entries, policy: snapshot == nil ? WidgetSnapshot.retryPolicy : .atEnd)
    }

    private func entry(at date: Date, configuration: SetlistConfigurationIntent, family: WidgetFamily, snapshot: WidgetSnapshot?) -> SetlistEntry {
        let pinned = configuration.setlist.flatMap { $0.isNextGig ? nil : $0.id }
        let identity = "\(pinned ?? SetlistEntity.nextGigId)#\(family.songRowCapacity)"
        return SetlistEntry(date: date, snapshot: snapshot, pinnedFileName: pinned, identity: identity, storedPage: SetlistPage.stored(for: identity))
    }
}

/// What the widget shows on its entry's day: the same rules as the Android widget's (`:presentation`'s
/// `WidgetContent.kt`), written again here, since the extension links no Kotlin.
enum SetlistContent {
    case shown(fileName: String, title: String, relativeDay: String, songs: [Song], link: String)
    case message(text: String, link: String)

    /// A song of the setlist shown, its number in it (from 1), and the link that opens the setlist at it.
    struct Song {
        let number: Int
        let title: String
        let artist: String
        let cover: String?
        let link: String
    }

    init(snapshot: WidgetSnapshot, pinnedFileName: String?, on date: Date) {
        let strings = snapshot.strings
        guard snapshot.areSongsEnabled else { self = .message(text: strings.songsOff, link: snapshot.homeLink); return }
        guard snapshot.areSetlistsEnabled else { self = .message(text: strings.setlistsOff, link: snapshot.homeLink); return }
        let today = Self.dayString(of: date)
        let setlist: WidgetSnapshot.Setlist
        if let pinnedFileName {
            guard let pinned = snapshot.setlists.first(where: { $0.fileName == pinnedFileName }) else {
                self = .message(text: strings.setlistMissing, link: snapshot.setlistsLink)
                return
            }
            setlist = pinned
        } else {
            // The earliest day from today on, the first of a day's in the snapshot's order, which is the setlists screen's.
            let upcoming = snapshot.setlists.filter { !$0.isArchived && Self.isDay($0.date) && $0.date >= today }
            guard let next = upcoming.min(by: { $0.date < $1.date }) else {
                self = .message(text: strings.noUpcomingSetlist, link: snapshot.setlistsLink)
                return
            }
            setlist = next
        }
        let library = Dictionary(snapshot.songs.map { ($0.fileName, $0) }, uniquingKeysWith: { _, last in last })
        self = .shown(
            fileName: setlist.fileName,
            title: setlist.title,
            relativeDay: Self.relativeDay(from: date, to: setlist.date, strings: strings),
            songs: setlist.songFileNames.enumerated().compactMap { index, fileName in
                // A snapshot from before the songs had links of their own opens the setlist at its first.
                library[fileName].map { song in
                    Song(number: index + 1, title: song.title, artist: song.artist, cover: song.cover, link: setlist.songLinks?[safe: index] ?? setlist.link)
                }
            },
            link: setlist.link
        )
    }

    var link: String {
        switch self {
        case .shown(_, _, _, _, let link), .message(_, let link): link
        }
    }

    private static func isDay(_ text: String) -> Bool { dayComponents(text) != nil }

    /// The snapshot's days are ISO dates, so they are read in the Gregorian calendar whatever calendar the system is set
    /// to (a Buddhist or Japanese year would never compare with them), in the system's time zone.
    private static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .autoupdatingCurrent
        return calendar
    }()

    /// `yyyy-MM-dd` of the day `date` falls on, which sorts as the days do.
    private static func dayString(of date: Date) -> String {
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", components.year ?? 0, components.month ?? 0, components.day ?? 0)
    }

    private static func dayComponents(_ text: String) -> DateComponents? {
        let parts = text.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3, text.count == 10 else { return nil }
        return DateComponents(year: parts[0], month: parts[1], day: parts[2])
    }

    private static func relativeDay(from date: Date, to day: String, strings: WidgetSnapshot.Strings) -> String {
        guard let components = dayComponents(day), let target = calendar.date(from: components),
              let days = calendar.dateComponents([.day], from: calendar.startOfDay(for: date), to: calendar.startOfDay(for: target)).day
        else { return "" }
        switch days {
        case 0: return strings.today
        case 1: return strings.tomorrow
        case -1: return strings.yesterday
        case let days where days > 1: return strings.inDays.format(days)
        default: return strings.daysAgo.format(-days)
        }
    }
}

struct SetlistWidgetView: View {
    let entry: SetlistEntry
    @Environment(\.widgetFamily) private var family
    @Environment(\.widgetContentMargins) private var margins

    var body: some View {
        if let snapshot = entry.snapshot {
            let content = SetlistContent(snapshot: snapshot, pinnedFileName: entry.pinnedFileName, on: entry.date)
            layout(content)
                .widgetURL(appLink(content.link))
                .campfireWidget(strings: snapshot.strings, theme: snapshot.theme)
        } else {
            NoSnapshotMessage().containerBackground(.background, for: .widget)
        }
    }

    @ViewBuilder
    private func layout(_ content: SetlistContent) -> some View {
        switch content {
        case .message(let text, _):
            if family == .accessoryRectangular {
                Text(verbatim: text).font(InterFont.medium(14)).padding(margins).frame(maxWidth: .infinity, alignment: .leading)
            } else {
                WidgetMessage(text: text)
            }
        case .shown(let fileName, let title, let relativeDay, let songs, _):
            switch family {
            case .accessoryRectangular:
                VStack(alignment: .leading, spacing: 0) {
                    Text(verbatim: title).font(InterFont.bold(15)).lineLimit(2).widgetAccentable()
                    Text(verbatim: relativeDay).font(InterFont.regular(14))
                }
                .padding(margins)
                .frame(maxWidth: .infinity, alignment: .leading)
            case .systemMedium, .systemLarge:
                // A page of the rows that fit, its songs keeping their numbers in the setlist; widgets cannot scroll.
                let capacity = family.songRowCapacity
                let pageCount = max((songs.count + capacity - 1) / capacity, 1)
                let page = SetlistPage.shown(stored: entry.storedPage, setlistFileName: fileName, pageCount: pageCount)
                let shown = Array(songs.dropFirst(page * capacity).prefix(capacity))
                let digits = String(shown.map(\.number).max() ?? 1).count
                VStack(alignment: .leading, spacing: 4) {
                    SetlistHeader(
                        title: title,
                        relativeDay: relativeDay,
                        pages: pageCount > 1
                            ? SetlistPageButtons(
                                identity: entry.identity, setlistFileName: fileName, page: page, pageCount: pageCount, strings: entry.snapshot?.strings
                            )
                            : nil
                    )
                        .padding(.leading, margins.leading)
                        .padding(.trailing, margins.trailing)
                    if songs.isEmpty, let empty = entry.snapshot?.strings.emptySetlist, !empty.isEmpty {
                        WidgetMessage(text: empty)
                    } else {
                        WidgetSongRows(count: capacity, rows: shown.map { song in
                            WidgetSongRow(title: song.title, artist: song.artist, cover: song.cover, link: song.link, number: song.number, numberDigits: digits)
                        })
                    }
                }
                .padding(.top, margins.top)
                .padding(.bottom, margins.bottom)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            default:
                VStack(alignment: .leading, spacing: 4) {
                    RelativeDayText(text: relativeDay)
                    Text(verbatim: title).font(InterFont.bold(17)).lineLimit(3)
                    Spacer(minLength: 0)
                }
                .padding(margins)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }
    }
}

/// What tells the widget from Random picks: the setlist's title where Random picks has its own, and how far away its day
/// is where Random picks has its button, followed by the page buttons where its songs need more than one page. The day
/// itself is left out, since how far away it is already says it.
private struct SetlistHeader: View {
    let title: String
    let relativeDay: String
    let pages: SetlistPageButtons?
    @Environment(\.widgetColors) private var colors

    var body: some View {
        HStack {
            Text(verbatim: title)
                .font(InterFont.bold(15))
                .foregroundStyle(colors.accent)
                .widgetAccentable()
                .lineLimit(1)
            Spacer(minLength: 8)
            Text(verbatim: relativeDay)
                .font(InterFont.medium(13, relativeTo: .caption))
                .foregroundStyle(colors.secondaryText)
                .lineLimit(1)
            if let pages {
                pages.padding(.leading, 4)
            }
        }
    }
}

private struct RelativeDayText: View {
    let text: String
    @Environment(\.widgetColors) private var colors

    var body: some View {
        Text(verbatim: text)
            .font(InterFont.bold(13, relativeTo: .caption))
            .foregroundStyle(colors.accent)
            .widgetAccentable()
    }
}

private extension Array {
    subscript(safe index: Int) -> Element? { indices.contains(index) ? self[index] : nil }
}
