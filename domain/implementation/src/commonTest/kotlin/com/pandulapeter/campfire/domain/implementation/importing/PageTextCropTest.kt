/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.importing

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.domain.api.importing.PageSongLines
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageTextCropTest {

    @Test
    fun `the lines the cropper leaves out are dropped and the blank lines between kept ones stay`() = runTest {
        val cropped = PageTextCrop.cropped(PAGE, FakePageCropper { songLines(2, 3, 5, 6) }, Logger { })

        assertEquals("G   D\nDown by the river\n\nC   G\nWe sang", cropped?.text)
        assertNull(cropped?.body)
    }

    @Test
    fun `a crop keeping every line is no crop`() = runTest {
        assertNull(PageTextCrop.cropped(PAGE, FakePageCropper { songLines(*PAGE.lines().indices.toList().toIntArray()) }, Logger { }))
    }

    @Test
    fun `no song and a failure both keep the whole text and the failure is logged`() = runTest {
        val logged = mutableListOf<String>()

        assertNull(PageTextCrop.cropped(PAGE, FakePageCropper { null }, Logger { logged += it }))
        assertNull(PageTextCrop.cropped(PAGE, FakePageCropper { PageSongLines(emptyList(), null, null, emptyList()) }, Logger { logged += it }))
        assertEquals(emptyList(), logged)
        assertNull(PageTextCrop.cropped(PAGE, FakePageCropper { error("No weights") }, Logger { logged += it }))
        assertEquals(1, logged.size)
    }

    @Test
    fun `lines the cropper names that the text does not have are ignored`() = runTest {
        assertEquals("Down by the river", PageTextCrop.cropped(PAGE, FakePageCropper { songLines(3, 99, -1, titleLine = 42, headerLines = listOf(42)) }, Logger { })?.text)
    }

    @Test
    fun `a title line before the song leaves the body and titles the song`() = runTest {
        val cropped = PageTextCrop.cropped(HEADED_PAGE, FakePageCropper { songLines(2, 4, 5, 7, 8, titleLine = 1, headerLines = listOf(1)) }, Logger { })
        val converted = cropped?.let(PageTextCrop::converted)

        assertEquals(true, cropped?.text?.startsWith("Wonderwall Chords by Oasis\n"))
        assertEquals(false, cropped?.body?.contains("Wonderwall"))
        assertEquals("Wonderwall", converted?.title)
        assertEquals("Oasis", converted?.artist)
        val song = converted?.songs?.single().orEmpty()
        assertTrue("Wonderwall" !in song && "gonna be the day" in song && "Strumming" in song)
        assertNull(ChordProParser.parseMetadata(song).title)
    }

    @Test
    fun `an artist line before the song names the artist without its label`() = runTest {
        val page = HEADED_PAGE.replace("Wonderwall Chords by Oasis", "Wonderwall\nArtist: Oasis")
        val cropped = PageTextCrop.cropped(page, FakePageCropper { songLines(3, 5, 6, 8, 9, titleLine = 1, artistLine = 2, headerLines = listOf(1, 2)) }, Logger { })
        val converted = cropped?.let(PageTextCrop::converted)

        assertEquals("Wonderwall", converted?.title)
        assertEquals("Oasis", converted?.artist)
        assertTrue("Oasis" !in converted?.songs?.single().orEmpty())
    }

    @Test
    fun `a title line that does not head the song stays in it and titles nothing`() = runTest {
        val cropped = PageTextCrop.cropped(HEADED_PAGE, FakePageCropper { songLines(2, 4, 5, 7, 8, titleLine = 8) }, Logger { })

        assertNull(cropped?.body)
        assertNull(cropped?.title)
        assertTrue("throw it back to you" in cropped?.let(PageTextCrop::converted)?.songs?.single().orEmpty())
    }

    @Test
    fun `a title the converter reads from the page's own lines wins over the title line's`() = runTest {
        val page = "Sign in\nOasis - Wonderwall\n\nEm7      G\nToday is gonna be the day\n\nDsus4    A7sus4\nThat they're gonna throw it back to you\n\nComments"
        val converted = PageTextCrop.cropped(page, FakePageCropper { songLines(3, 4, 6, 7, titleLine = 1, headerLines = listOf(1)) }, Logger { })
            ?.let(PageTextCrop::converted)

        assertNull(converted?.title)
        assertEquals("Wonderwall", ChordProParser.parseMetadata(converted?.songs?.single().orEmpty()).title)
    }

    @Test
    fun `the cropped lines keep the blank lines between kept ones and leave the heading out of the body`() {
        val cropped = PageTextCrop.croppedLines(HEADED_PAGE.lines(), songLines(2, 4, 5, 7, 8, titleLine = 1, headerLines = listOf(1)))

        assertEquals(PageTextCrop.CroppedLines(withHeader = (1..8).toList(), body = (2..8).toList(), heading = setOf(1)), cropped)
    }

    private companion object {
        const val PAGE = "Menu\nSign in\nG   D\nDown by the river\n\nC   G\nWe sang\n\nComments"
        const val HEADED_PAGE = "Sign in\nWonderwall Chords by Oasis\nStrumming: down down up\n\nEm7      G\nToday is gonna be the day\n\n" +
            "Dsus4    A7sus4\nThat they're gonna throw it back to you\n\nComments"
    }
}
