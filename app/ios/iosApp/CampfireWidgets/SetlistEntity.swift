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
import WidgetKit

/// What a setlist widget shows: Next gig, or one setlist kept by its file name, the identity setlists have everywhere.
/// Listed from the snapshot, so the choices are the app's setlists as it last wrote them.
struct SetlistEntity: AppEntity {
    /// Next gig's: no setlist file is named so, since every one ends in `.setlist.json`.
    static let nextGigId = "next-gig"

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Setlist"
    static var defaultQuery = SetlistQuery()

    let id: String
    /// Nil for Next gig, which is named in the system's language.
    let title: String?

    static let nextGig = SetlistEntity(id: nextGigId, title: nil)

    var isNextGig: Bool { id == Self.nextGigId }

    var displayRepresentation: DisplayRepresentation {
        if let title { DisplayRepresentation(title: "\(title)") } else { DisplayRepresentation(title: "Next gig") }
    }
}

struct SetlistQuery: EntityQuery {
    func entities(for identifiers: [SetlistEntity.ID]) async throws -> [SetlistEntity] {
        let setlists = WidgetSnapshot.load()?.setlists ?? []
        return identifiers.compactMap { id in
            if id == SetlistEntity.nextGigId { return .nextGig }
            // One no longer in the library keeps its place, the widget saying it is gone, until another is chosen.
            return SetlistEntity(id: id, title: setlists.first { $0.fileName == id }?.title ?? id)
        }
    }

    func suggestedEntities() async throws -> [SetlistEntity] {
        [.nextGig] + (WidgetSnapshot.load()?.setlists ?? []).map { SetlistEntity(id: $0.fileName, title: $0.title) }
    }

    func defaultResult() async -> SetlistEntity? { .nextGig }
}

/// The setlist widget's one setting, asked when it is added and from Edit Widget.
struct SetlistConfigurationIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Setlist"
    static var description = IntentDescription("Choose which setlist the widget shows.")

    @Parameter(title: "Setlist")
    var setlist: SetlistEntity?
}
