/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.mapper

import com.pandulapeter.campfire.data.model.domain.PrintSettings
import com.pandulapeter.campfire.data.source.local.implementation.model.PrintSettingsDocument
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.local.implementation.model.UserPreferencesDocument

internal fun UserPreferencesDocument.toModel() = UserPreferences(
    isPerformanceModeEnabled = isPerformanceModeEnabled,
    shouldShowArchivedSetlists = shouldShowArchivedSetlists,
    isLyricsOnlyModeEnabled = isLyricsOnlyModeEnabled,
    // A hand edit or a newer version's wider range must not reach the screen as it is: a size of 40 is a column per
    // word. Not a number at all is no size, and is the default.
    fontScale = fontScale.takeIf { it.isFinite() }?.coerceIn(UserPreferences.MIN_FONT_SCALE, UserPreferences.MAX_FONT_SCALE)
        ?: UserPreferences.DEFAULT_FONT_SCALE,
    sortingMode = UserPreferences.SortingMode.entries.firstOrNull { it.id == sortingMode } ?: UserPreferences.SortingMode.BY_ARTIST,
    setlistSortingMode = UserPreferences.SetlistSortingMode.entries.firstOrNull { it.id == setlistSortingMode } ?: UserPreferences.SetlistSortingMode.BY_DATE,
    uiMode = UserPreferences.UiMode.entries.firstOrNull { it.id == uiMode } ?: UserPreferences.UiMode.SYSTEM_DEFAULT,
    themeColor = UserPreferences.ThemeColor.entries.firstOrNull { it.id == themeColor } ?: UserPreferences.ThemeColor.CAMPFIRE,
    isAppIconThemed = isAppIconThemed,
    isCoverArtEnabled = isCoverArtEnabled,
    shouldNumberSections = shouldNumberSections,
    language = UserPreferences.Language.entries.firstOrNull { it.id == language } ?: UserPreferences.Language.SYSTEM_DEFAULT,
    chordSpelling = UserPreferences.ChordSpelling(
        accidentals = UserPreferences.Accidentals.entries.firstOrNull { it.id == accidentals } ?: UserPreferences.Accidentals.ORIGINAL,
        notation = UserPreferences.Notation.entries.firstOrNull { it.id == notation }
            ?: if (isGermanNotationEnabled) UserPreferences.Notation.GERMAN else UserPreferences.Notation.STANDARD,
    ),
    transpositions = transpositions,
    foldedSections = foldedSections.mapValues { (_, keys) -> keys.toSet() }.filterValues { it.isNotEmpty() },
    tagMatchMode = UserPreferences.MatchMode.entries.firstOrNull { it.id == tagMatchMode } ?: UserPreferences.MatchMode.ANY,
    languageMatchMode = UserPreferences.MatchMode.entries.firstOrNull { it.id == languageMatchMode } ?: UserPreferences.MatchMode.ANY,
    tagSortingMode = UserPreferences.LabelSortingMode.entries.firstOrNull { it.id == tagSortingMode } ?: UserPreferences.LabelSortingMode.BY_USAGE,
    languageSortingMode = UserPreferences.LabelSortingMode.entries.firstOrNull { it.id == languageSortingMode } ?: UserPreferences.LabelSortingMode.BY_USAGE,
    printSettings = printSettings.toModel(),
    seenWhatsNewVersions = seenWhatsNewVersions,
)

internal fun UserPreferences.toDocument() = UserPreferencesDocument(
    isPerformanceModeEnabled = isPerformanceModeEnabled,
    shouldShowArchivedSetlists = shouldShowArchivedSetlists,
    isLyricsOnlyModeEnabled = isLyricsOnlyModeEnabled,
    fontScale = fontScale,
    sortingMode = sortingMode.id,
    setlistSortingMode = setlistSortingMode.id,
    uiMode = uiMode.id,
    themeColor = themeColor.id,
    isAppIconThemed = isAppIconThemed,
    isCoverArtEnabled = isCoverArtEnabled,
    shouldNumberSections = shouldNumberSections,
    language = language.id,
    accidentals = chordSpelling.accidentals.id,
    notation = chordSpelling.notation.id,
    transpositions = transpositions,
    foldedSections = foldedSections.mapValues { (_, keys) -> keys.toList() },
    tagMatchMode = tagMatchMode.id,
    languageMatchMode = languageMatchMode.id,
    tagSortingMode = tagSortingMode.id,
    languageSortingMode = languageSortingMode.id,
    printSettings = printSettings.toDocument(),
    seenWhatsNewVersions = seenWhatsNewVersions,
)

internal fun PrintSettingsDocument.toModel() = PrintSettings(
    format = PrintSettings.Format.entries.firstOrNull { it.id == format } ?: PrintSettings.Format.PDF,
    paper = PrintSettings.Paper.entries.firstOrNull { it.id == paper } ?: PrintSettings.Paper.A4,
    setlistMode = PrintSettings.SetlistMode.entries.firstOrNull { it.id == setlistMode } ?: PrintSettings.SetlistMode.SONG_SHEETS,
    isLandscape = isLandscape,
    fontSize = fontSize,
    marginMm = marginMm,
    columns = columns,
    showChords = showChords,
    showComments = showComments,
    showMetadata = showMetadata,
    showPageNumbers = showPageNumbers,
    startSongsOnNewPage = startSongsOnNewPage,
    includeSetlistOverview = includeSetlistOverview,
).normalized()

internal fun PrintSettings.toDocument() = normalized().let { settings ->
    PrintSettingsDocument(
        format = settings.format.id,
        paper = settings.paper.id,
        setlistMode = settings.setlistMode.id,
        isLandscape = settings.isLandscape,
        fontSize = settings.fontSize,
        marginMm = settings.marginMm,
        columns = settings.columns,
        showChords = settings.showChords,
        showComments = settings.showComments,
        showMetadata = settings.showMetadata,
        showPageNumbers = settings.showPageNumbers,
        startSongsOnNewPage = settings.startSongsOnNewPage,
        includeSetlistOverview = settings.includeSetlistOverview,
    )
}
