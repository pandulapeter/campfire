/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class UserPreferencesSongMapsTest {

    @Test
    fun `a renamed song takes everything kept for it along`() {
        val renamed = preferences(SONG).withSongRenamed(SONG, NEW_SONG)

        assertEquals(mapOf(NEW_SONG to 2), renamed.transpositions)
        assertEquals(mapOf(NEW_SONG to 92), renamed.tempos)
        assertEquals(mapOf(NEW_SONG to 3), renamed.capos)
        assertEquals(mapOf(NEW_SONG to setOf("chorus")), renamed.foldedSections)
    }

    @Test
    fun `a deleted song takes everything kept for it away`() {
        val deleted = preferences(SONG).withSongRenamed(SONG, null)

        assertEquals(emptyMap(), deleted.transpositions)
        assertEquals(emptyMap(), deleted.tempos)
        assertEquals(emptyMap(), deleted.capos)
        assertEquals(emptyMap(), deleted.foldedSections)
    }

    @Test
    fun `a song with nothing kept changes nothing`() {
        val preferences = preferences(SONG)

        assertEquals(preferences, preferences.withSongRenamed("other.cho", NEW_SONG))
        assertSame(preferences.transpositions, preferences.withSongRenamed("other.cho", NEW_SONG).transpositions)
    }

    @Test
    fun `the moved entry wins over one already under the new name`() {
        val preferences = preferences(SONG).copy(transpositions = mapOf(SONG to 2, NEW_SONG to -1))

        assertEquals(mapOf(NEW_SONG to 2), preferences.withSongRenamed(SONG, NEW_SONG).transpositions)
    }

    @Test
    fun `an emptied library keeps nothing for any song and everything else`() {
        val preferences = preferences(SONG).copy(
            chordVoicings = mapOf("guitar" to mapOf("F:0.4.7" to "x x 3 2 1 1")),
            demoLibraryContentHashes = mapOf("songs/demo.cho" to "0a1b"),
        )

        assertEquals(
            preferences.copy(transpositions = emptyMap(), tempos = emptyMap(), capos = emptyMap(), foldedSections = emptyMap()),
            preferences.withoutSongOverrides(),
        )
    }

    private fun preferences(fileName: String) = UserPreferences(
        isPerformanceModeEnabled = false,
        shouldShowArchivedSetlists = false,
        areChordsEnabled = true,
        areSetlistsEnabled = true,
        isMetronomeEnabled = true,
        fontScale = UserPreferences.DEFAULT_FONT_SCALE,
        sortingMode = UserPreferences.SortingMode.BY_TITLE,
        setlistSortingMode = UserPreferences.SetlistSortingMode.BY_DATE,
        uiMode = UserPreferences.UiMode.SYSTEM_DEFAULT,
        themeColor = UserPreferences.ThemeColor.CAMPFIRE,
        isAppIconThemed = true,
        isCoverArtEnabled = true,
        shouldNumberSections = true,
        language = UserPreferences.Language.SYSTEM_DEFAULT,
        chordSpelling = UserPreferences.ChordSpelling.Default,
        transpositions = mapOf(fileName to 2),
        tempos = mapOf(fileName to 92),
        capos = mapOf(fileName to 3),
        foldedSections = mapOf(fileName to setOf("chorus")),
        tagMatchMode = UserPreferences.MatchMode.ANY,
        languageMatchMode = UserPreferences.MatchMode.ANY,
        tagSortingMode = UserPreferences.LabelSortingMode.BY_USAGE,
        languageSortingMode = UserPreferences.LabelSortingMode.BY_USAGE,
    )

    private companion object {
        const val SONG = "song.cho"
        const val NEW_SONG = "new_song.cho"
    }
}
