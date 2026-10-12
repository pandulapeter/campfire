/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields.Field
import com.pandulapeter.campfire.data.model.domain.Catalogue
import com.pandulapeter.campfire.data.model.domain.SongCredits
import com.pandulapeter.campfire.data.model.domain.SongDetailsCandidate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SongDetailsReviewTest {

    @Test
    fun `an empty field is ticked, a filled one is offered unticked, and an identical one is left out`() {
        val draft = mapOf(Field.TITLE to "Wonderwall", Field.ARTIST to "oasis", Field.ALBUM to "")

        val rows = songDetailsReviewRows(draft, CANDIDATE, credits = null)

        assertEquals(listOf(Field.ARTIST, Field.ALBUM, Field.YEAR, Field.DURATION), rows.map { it.field })
        assertFalse(rows.first { it.field == Field.ARTIST }.isTickedByDefault)
        assertEquals("oasis", rows.first { it.field == Field.ARTIST }.current)
        assertTrue(rows.first { it.field == Field.ALBUM }.isTickedByDefault)
    }

    @Test
    fun `the title is never ticked, even over an empty one`() {
        val row = songDetailsReviewRows(emptyMap(), CANDIDATE, credits = null).first { it.field == Field.TITLE }

        assertFalse(row.isTickedByDefault)
    }

    @Test
    fun `durations are compared as durations`() {
        val draft = mapOf(Field.DURATION to durationDigitsOf("04:19"))

        assertTrue(songDetailsReviewRows(draft, CANDIDATE, credits = null).none { it.field == Field.DURATION })
        assertEquals(
            "4:19",
            songDetailsReviewRows(emptyMap(), CANDIDATE, credits = null).first { it.field == Field.DURATION }.suggested,
        )
    }

    @Test
    fun `the songwriters join the rows once they arrive, and a suggestion is cleaned of braces and line breaks`() {
        val rows = songDetailsReviewRows(emptyMap(), CANDIDATE, SongCredits(composer = "Noel {Gallagher}\n", lyricist = null))

        assertEquals("Noel Gallagher", rows.first { it.field == Field.COMPOSER }.suggested)
        assertTrue(rows.none { it.field == Field.LYRICIST })
    }

    @Test
    fun `a year that is not four digits is not offered`() {
        val rows = songDetailsReviewRows(emptyMap(), CANDIDATE.copy(year = "95"), credits = null)

        assertTrue(rows.none { it.field == Field.YEAR })
    }

    @Test
    fun `only the ticked suggestions are filled into the draft, the duration as its digits`() {
        val draft = mapOf(Field.TITLE to "Wonderwall", Field.ARTIST to "oasis", Field.SUBTITLE to "Acoustic")
        val rows = songDetailsReviewRows(draft, CANDIDATE, credits = null)

        val filled = draft.withSongDetails(rows, ticked = setOf(Field.ALBUM, Field.DURATION))

        assertEquals(
            mapOf(
                Field.TITLE to "Wonderwall",
                Field.ARTIST to "oasis",
                Field.SUBTITLE to "Acoustic",
                Field.ALBUM to "(What's the Story) Morning Glory?",
                Field.DURATION to "419",
            ),
            filled,
        )
    }

    private companion object {
        val CANDIDATE = SongDetailsCandidate(
            catalogue = Catalogue.ITUNES,
            id = "1",
            title = "Wonderwall",
            artist = "Oasis",
            album = "(What's the Story) Morning Glory?",
            year = "1995",
            duration = 259.seconds,
        )
    }
}
