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

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences.SortingMode
import com.pandulapeter.campfire.domain.api.models.SongSection
import kotlin.test.Test
import kotlin.test.assertEquals

/** The order of the song list and the headers it is listed under, which the fast scroller and the sections rely on. */
class SongSortingTest {

    @Test
    fun `titles that start with no letter share one section, before the letters`() {
        val sections = sections(SortingMode.BY_TITLE, song("Yesterday"), song("😀 Smile"), song("¿Quién será?"), song("1999"), song("Hey Jude"))

        assertEquals(listOf(SongSection.Header.Symbols, SongSection.Header.Letter('H'), SongSection.Header.Letter('Y')), sections.map { it.header })
        assertEquals(listOf("1999", "¿Quién será?", "😀 Smile"), sections.first().songs.map { it.title })
    }

    @Test
    fun `two lower case letters with one upper case share a header`() {
        val sections = sections(SortingMode.BY_TITLE, song("Imagine"), song("ıhlamur"))

        assertEquals(listOf(SongSection.Header.Letter('I')), sections.map { it.header })
    }

    @Test
    fun `an artist spelled with and without an accent is one section`() {
        val sections = sections(SortingMode.BY_ARTIST, song("Halo", artist = "Beyoncé"), song("Single Ladies", artist = "Beyonce"))

        assertEquals(listOf(listOf("Halo", "Single Ladies")), sections.map { section -> section.songs.map { it.title } })
    }

    @Test
    fun `songs that tie on everything else are in the order of their file names`() {
        val sorted = listOf(song("Yesterday", fileName = "yesterday_2.cho"), song("Yesterday", fileName = "yesterday.cho"))
            .sortedFor(SortingMode.BY_TITLE, NormalizeTextUseCaseImpl()::invoke)

        assertEquals(listOf("yesterday.cho", "yesterday_2.cho"), sorted.songs.map { it.fileName })
    }

    private fun sections(sortingMode: SortingMode, vararg songs: Song): List<SongSection> {
        val sorted = songs.toList().sortedFor(sortingMode, NormalizeTextUseCaseImpl()::invoke)
        return sorted.songs.map { sorted.byFileName.getValue(it.fileName) }.cutIntoSections(sortingMode)
    }

    private fun song(title: String, artist: String = "", fileName: String = "$title.cho") = testSong(fileName = fileName, title = title, artist = artist)
}
