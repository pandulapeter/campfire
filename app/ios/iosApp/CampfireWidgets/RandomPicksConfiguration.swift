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

/// A tag of the library a Random picks widget can draw from, kept by its text as the songs write it. Listed from the
/// snapshot, so the choices are the tags the app last wrote.
struct TagEntity: AppEntity {
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Tag"
    static var defaultQuery = TagQuery()

    let id: String

    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(id)") }
}

struct TagQuery: EntityQuery {
    /// A tag no longer in the library keeps its place until it is removed, the filter ignoring it meanwhile.
    func entities(for identifiers: [TagEntity.ID]) async throws -> [TagEntity] {
        identifiers.map(TagEntity.init(id:))
    }

    func suggestedEntities() async throws -> [TagEntity] {
        (WidgetSnapshot.load()?.tags ?? []).map(TagEntity.init(id:))
    }
}

/// A language of the library a Random picks widget can draw from, kept by its code (`und` for the songs that name none)
/// and shown by the name the app wrote in its own language.
struct LanguageEntity: AppEntity {
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Language"
    static var defaultQuery = LanguageQuery()

    let id: String
    let name: String

    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }
}

struct LanguageQuery: EntityQuery {
    /// A language no longer in the library keeps its place, named by its code, until it is removed.
    func entities(for identifiers: [LanguageEntity.ID]) async throws -> [LanguageEntity] {
        let languages = WidgetSnapshot.load()?.languages ?? []
        return identifiers.map { code in LanguageEntity(id: code, name: languages.first { $0.code == code }?.name ?? code) }
    }

    func suggestedEntities() async throws -> [LanguageEntity] {
        (WidgetSnapshot.load()?.languages ?? []).map { LanguageEntity(id: $0.code, name: $0.name) }
    }
}

/// The Random picks widget's settings, from Edit Widget: a title of its own, and the tags and languages its picks come
/// from. A widget added before they existed has none of them, so it draws from every song under the snapshot's title.
/// How many it draws is its size's, never a setting: as many as fill it (`songRowCapacity`).
struct RandomPicksConfigurationIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Random picks"
    static var description = IntentDescription(
        "Choose the widget's title, and the tags and languages its picks come from. With none chosen in a group, that group leaves every song in."
    )

    @Parameter(title: "Title")
    var title: String?

    @Parameter(title: "Tags")
    var tags: [TagEntity]?

    @Parameter(title: "Languages")
    var languages: [LanguageEntity]?

    var filter: RandomPicksFilter {
        RandomPicksFilter(tags: Set((tags ?? []).map(\.id)), languages: Set((languages ?? []).map(\.id)))
    }
}

/// What a Random picks widget draws from, as `:presentation`'s `RandomPicksFilter` selects: the songs with any of `tags`,
/// matched without regard to case, and in any of `languages` (`und` for those naming none). A group that is empty, or
/// whose every value the library no longer has, leaves the songs as they are.
struct RandomPicksFilter {
    var tags: Set<String> = []
    var languages: Set<String> = []

    func songs(of snapshot: WidgetSnapshot) -> [WidgetSnapshot.Song] {
        let libraryTags = Set((snapshot.tags ?? []).map(Self.tagKey))
        let selectedTags = Set(tags.map(Self.tagKey)).intersection(libraryTags)
        let selectedLanguages = languages.intersection((snapshot.languages ?? []).map(\.code))
        return snapshot.songs.filter { song in
            let songLanguages = song.languages ?? []
            return (selectedTags.isEmpty || (song.tags ?? []).contains { selectedTags.contains(Self.tagKey($0)) }) &&
                (selectedLanguages.isEmpty || (songLanguages.isEmpty ? [Self.unknownLanguage] : songLanguages).contains(where: selectedLanguages.contains))
        }
    }

    /// The code the app writes for the songs that name no language (`SongLanguage.UNKNOWN`).
    static let unknownLanguage = "und"

    /// A tag as the app compares it (`tagKey`): one Unicode form, and no case.
    private static func tagKey(_ tag: String) -> String { tag.precomposedStringWithCanonicalMapping.lowercased() }
}
