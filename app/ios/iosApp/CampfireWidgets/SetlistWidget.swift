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

/// The next gig, or a setlist the user chose, and how far away its day is. The whole widget is one tap, which opens the
/// setlist's first song read from it (or the setlists screen, where there is no setlist to show).
struct SetlistWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "Setlist", intent: SetlistConfigurationIntent.self, provider: SetlistProvider()) { entry in
            SetlistWidgetView(entry: entry)
        }
        .configurationDisplayName("Setlist")
        .description("Your next gig, or a setlist you choose, and how many days are left.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular])
    }
}

struct SetlistEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot?
    /// The file name of the setlist chosen, nil for Next gig.
    let pinnedFileName: String?
}

/// An entry now and one at each of the week's next midnights: "In 3 days" becomes "In 2 days", and a next gig whose day
/// has passed gives way to the one after it.
struct SetlistProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> SetlistEntry {
        SetlistEntry(date: Date(), snapshot: nil, pinnedFileName: nil)
    }

    func snapshot(for configuration: SetlistConfigurationIntent, in context: Context) async -> SetlistEntry {
        entry(at: Date(), configuration: configuration, snapshot: WidgetSnapshot.load())
    }

    func timeline(for configuration: SetlistConfigurationIntent, in context: Context) async -> Timeline<SetlistEntry> {
        let snapshot = WidgetSnapshot.load()
        let now = Date()
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: now)
        let midnights = (1...7).compactMap { calendar.date(byAdding: .day, value: $0, to: today) }
        let entries = ([now] + midnights).map { entry(at: $0, configuration: configuration, snapshot: snapshot) }
        return Timeline(entries: entries, policy: snapshot == nil ? WidgetSnapshot.retryPolicy : .atEnd)
    }

    private func entry(at date: Date, configuration: SetlistConfigurationIntent, snapshot: WidgetSnapshot?) -> SetlistEntry {
        let pinned = configuration.setlist.flatMap { $0.isNextGig ? nil : $0.id }
        return SetlistEntry(date: date, snapshot: snapshot, pinnedFileName: pinned)
    }
}

/// What the widget shows on its entry's day: the same rules as the Android widget's (`:presentation`'s
/// `WidgetContent.kt`), written again here, since the extension links no Kotlin.
enum SetlistContent {
    case shown(title: String, date: String, relativeDay: String, songCount: String, songTitles: [String], link: String)
    case message(text: String, link: String)

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
        let titles = Dictionary(snapshot.songs.map { ($0.fileName, $0.title) }, uniquingKeysWith: { first, _ in first })
        self = .shown(
            title: setlist.title,
            date: setlist.formattedDate,
            relativeDay: Self.relativeDay(from: date, to: setlist.date, strings: strings),
            songCount: strings.songCount.format(setlist.songFileNames.count),
            songTitles: setlist.songFileNames.compactMap { titles[$0] },
            link: setlist.link
        )
    }

    var link: String {
        switch self {
        case .shown(_, _, _, _, _, let link), .message(_, let link): link
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

    /// Its own, since the theme is put in the environment of the view it draws rather than its own.
    private var colors: WidgetColors { WidgetColors(theme: entry.snapshot?.theme) }

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
                Text(verbatim: text).font(InterFont.medium(14)).frame(maxWidth: .infinity, alignment: .leading)
            } else {
                WidgetMessage(text: text)
            }
        case .shown(let title, let date, let relativeDay, let songCount, let songTitles, _):
            switch family {
            case .accessoryRectangular:
                VStack(alignment: .leading, spacing: 0) {
                    Text(verbatim: title).font(InterFont.bold(15)).lineLimit(2).widgetAccentable()
                    Text(verbatim: relativeDay).font(InterFont.regular(14))
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            case .systemMedium:
                HStack(alignment: .top, spacing: 16) {
                    VStack(alignment: .leading, spacing: 4) {
                        RelativeDayText(text: relativeDay)
                        Text(verbatim: title).font(InterFont.bold(17)).lineLimit(2)
                        Text(verbatim: date).font(InterFont.regular(13, relativeTo: .caption)).foregroundStyle(colors.secondaryText)
                        Spacer(minLength: 0)
                        Text(verbatim: songCount).font(InterFont.regular(13, relativeTo: .caption)).foregroundStyle(colors.secondaryText)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    SongTitles(titles: songTitles)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            default:
                VStack(alignment: .leading, spacing: 4) {
                    RelativeDayText(text: relativeDay)
                    Text(verbatim: title).font(InterFont.bold(17)).lineLimit(3)
                    Spacer(minLength: 0)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
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

/// The first songs of the setlist, in order, as many as fit.
private struct SongTitles: View {
    let titles: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(Array(titles.prefix(5).enumerated()), id: \.offset) { index, title in
                Text(verbatim: "\(index + 1). \(title)")
                    .font(InterFont.regular(13, relativeTo: .caption))
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
