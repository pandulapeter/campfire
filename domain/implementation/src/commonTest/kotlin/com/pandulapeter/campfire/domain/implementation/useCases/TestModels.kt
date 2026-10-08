/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import kotlinx.datetime.LocalDate

/** A song as the scan lists it, titled after its file name unless the test cares about the title. */
internal fun testSong(
    fileName: String,
    title: String = fileName.substringBeforeLast('.'),
    artist: String = "",
    tags: List<String> = emptyList(),
    languages: List<String> = emptyList(),
) = Song(
    fileName = fileName,
    title = title,
    artist = artist,
    key = null,
    transpose = 0,
    tags = tags,
    languages = languages,
    coverArtUrl = null,
    hasChords = true,
    canUpdateFileName = false,
    lastModified = 0L,
    size = 0L,
)

/** A setlist naming [entries] by their file names, titled after its own file name unless the test cares about the title. */
internal fun testSetlist(
    fileName: String,
    title: String = fileName.removeSuffix(".setlist.json"),
    entries: List<String> = emptyList(),
    date: LocalDate = LocalDate(2026, 1, 1),
    isArchived: Boolean = false,
) = Setlist(
    fileName = fileName,
    title = title,
    description = "",
    date = date,
    isArchived = isArchived,
    entries = entries.map { Setlist.Entry(songFileName = it) },
    size = 0L,
)

/** Preferences with nothing saved for any song, sorted by title. */
internal val TEST_PREFERENCES = UserPreferences(
    isPerformanceModeEnabled = false,
    shouldShowArchivedSetlists = false,
    areChordsEnabled = true,
    areSetlistsEnabled = true,
    isMetronomeEnabled = true,
    fontScale = 1f,
    sortingMode = UserPreferences.SortingMode.BY_TITLE,
    setlistSortingMode = UserPreferences.SetlistSortingMode.BY_DATE,
    uiMode = UserPreferences.UiMode.SYSTEM_DEFAULT,
    themeColor = UserPreferences.ThemeColor.CAMPFIRE,
    isAppIconThemed = true,
    isCoverArtEnabled = true,
    shouldNumberSections = true,
    language = UserPreferences.Language.SYSTEM_DEFAULT,
    chordSpelling = UserPreferences.ChordSpelling.Default,
    transpositions = emptyMap(),
    foldedSections = emptyMap(),
    tagMatchMode = UserPreferences.MatchMode.ANY,
    languageMatchMode = UserPreferences.MatchMode.ANY,
    tagSortingMode = UserPreferences.LabelSortingMode.BY_USAGE,
    languageSortingMode = UserPreferences.LabelSortingMode.BY_USAGE,
)
