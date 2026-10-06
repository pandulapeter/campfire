/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.source.local.implementation.mapper.toDocument
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistDocument
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistDocumentFormat
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistSongDocument
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Two devices that read one undated setlist on different days hold two files that differ only in that day, which sync
 * takes for one setlist on the strength of these answers - so everything else a document holds has to count.
 */
internal class SetlistComparisonImplTest {

    private val comparison = SetlistComparisonImpl()

    @Test
    fun twoDaysOfOneSetlistAreTheSame() = assertTrue(
        comparison.isSameApartFromDate(DOCUMENT.copy(date = "2026-10-05").bytes(), DOCUMENT.copy(date = "2026-10-07").bytes()),
    )

    @Test
    fun anUndatedAndADatedCopyAreTheSame() = assertTrue(
        comparison.isSameApartFromDate(DOCUMENT.bytes(), DOCUMENT.copy(date = "2026-10-07").bytes()),
    )

    @Test
    fun theDroppedPriorityDoesNotCount() = assertTrue(
        comparison.isSameApartFromDate(
            """{"title":"Summer","priority":3,"songs":[{"file":"a.cho"},{"file":"b.cho","transposition":2}]}""".encodeToByteArray(),
            DOCUMENT.copy(date = "2026-10-07").bytes(),
        ),
    )

    @Test
    fun everythingElseCounts() = listOf(
        DOCUMENT.copy(isCountdownShown = true),
        DOCUMENT.copy(title = "Winter"),
        DOCUMENT.copy(songs = DOCUMENT.songs.reversed()),
        DOCUMENT.copy(songs = listOf(DOCUMENT.songs.first(), DOCUMENT.songs.last().copy(transposition = 3))),
        DOCUMENT.copy(unknownFields = JsonObject(mapOf("venue" to JsonPrimitive("Hall")))),
    ).forEach { other ->
        assertFalse(comparison.isSameApartFromDate(DOCUMENT.copy(date = "2026-10-05").bytes(), other.copy(date = "2026-10-07").bytes()))
    }

    @Test
    fun aFileThatIsNoSetlistIsNeverTheSame() =
        assertFalse(comparison.isSameApartFromDate("{title".encodeToByteArray(), DOCUMENT.bytes()))

    @Test
    fun withoutTheDayADatedFileIsTheUndatedOneItWasWrittenFrom() = listOf(
        DOCUMENT,
        DOCUMENT.copy(songs = DOCUMENT.songs + SetlistSongDocument(file = "c.cho", tempo = 96, capo = 2)),
    ).forEach { document ->
        val undated = document.bytes()
        val dated = SetlistDocumentFormat.decode(undated.decodeToString())
            .toModel("summer.setlist.json", size = 0, undatedDay = LocalDate(2026, 10, 7))
            .toDocument()
            .bytes()

        assertContentEquals(undated, comparison.withoutDate(dated))
    }

    @Test
    fun aFileThatIsNoSetlistHasNoUndatedForm() = assertNull(comparison.withoutDate("not json".encodeToByteArray()))

    private fun SetlistDocument.bytes() = SetlistDocumentFormat.encode(this).encodeToByteArray()

    private companion object {
        val DOCUMENT = SetlistDocument(
            title = "Summer",
            songs = listOf(SetlistSongDocument(file = "a.cho"), SetlistSongDocument(file = "b.cho", transposition = 2)),
        )
    }
}
