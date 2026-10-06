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

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistDocument
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistDocumentFormat
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistSongDocument
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A setlist file is the one thing the user can hand-edit into a shape the screens cannot draw: the rows and the
 * pages are keyed by the song's file name, so a song named twice has to be read as named once, and a date that is
 * not one has to be read as no date, which gives the setlist the day it is read on, rather than as a file that cannot
 * be read.
 */
internal class SetlistMappersTest {

    @Test
    fun aSongNamedTwiceIsReadOnceWithItsFirstTransposition() {
        val document = SetlistDocument(
            title = "Summer",
            songs = listOf(
                SetlistSongDocument(file = "a.cho", transposition = 2, unknownFields = JsonObject(mapOf("note" to JsonPrimitive("x")))),
                SetlistSongDocument(file = ""),
                SetlistSongDocument(file = "b.cho"),
                SetlistSongDocument(file = "a.cho", transposition = -1),
            ),
        )
        assertEquals(
            expected = listOf(
                Setlist.Entry(songFileName = "a.cho", transposition = 2, unknownFields = """{"note":"x"}"""),
                Setlist.Entry(songFileName = "b.cho"),
            ),
            actual = document.toModel("summer.setlist.json", size = 0, undatedDay = DAY).entries,
        )
    }

    @Test
    fun aSongNamedTwiceIsWrittenOnce() {
        val setlist = Setlist(
            fileName = "summer.setlist.json",
            title = "Summer",
            description = "",
            date = LocalDate(2026, 1, 1),
            isArchived = false,
            entries = listOf(
                Setlist.Entry(songFileName = "a.cho", transposition = 2),
                Setlist.Entry(songFileName = "a.cho"),
            ),
            size = 0L,
        )
        assertEquals(
            expected = listOf(SetlistSongDocument(file = "a.cho", transposition = 2)),
            actual = setlist.toDocument().songs,
        )
    }

    @Test
    fun theDateIsWrittenAsAnIsoDateAndReadBack() {
        val setlist = SetlistDocument(title = "Summer", date = "2026-09-28").toModel("summer.setlist.json", size = 0, undatedDay = DAY)

        assertEquals(LocalDate(2026, 9, 28), setlist.date)
        assertEquals("2026-09-28", setlist.toDocument().date)
    }

    @Test
    fun theCountdownIsWrittenOnlyWhereItIsShown() {
        val setlist = SetlistDocumentFormat.decode("""{"title":"Summer","isCountdownShown":true}""").toModel("summer.setlist.json", size = 0, undatedDay = DAY)

        assertTrue(setlist.isCountdownShown)
        assertTrue("isCountdownShown" in SetlistDocumentFormat.encode(setlist.toDocument()))
        assertFalse("isCountdownShown" in SetlistDocumentFormat.encode(setlist.copy(isCountdownShown = false).toDocument()))
    }

    @Test
    fun aDateThatIsNotOneIsNoDateAndTheSetlistGetsTheDayItIsHanded() {
        (listOf("next friday", "2026-02-30", "", "2026-13-01T10:00", "T") + null).forEach { text ->
            val document = SetlistDocument(title = "Summer", date = text)

            assertFalse(document.isDated)
            assertEquals(DAY, document.toModel("summer.setlist.json", size = 0, undatedDay = DAY).date)
        }
        assertTrue(SetlistDocument(title = "Summer", date = "2026-09-28").isDated)
    }

    @Test
    fun theDayOfADateWrittenWithATimeIsTheSetlistsDay() = listOf("2026-10-06T20:00:00", " 2026-10-06T20:00Z ").forEach { text ->
        val document = SetlistDocument(title = "Summer", date = text)

        assertTrue(document.isDated)
        assertEquals(LocalDate(2026, 10, 6), document.toModel("summer.setlist.json", size = 0, undatedDay = DAY).date)
    }

    private companion object {
        val DAY = LocalDate(2025, 5, 5)
    }
}
