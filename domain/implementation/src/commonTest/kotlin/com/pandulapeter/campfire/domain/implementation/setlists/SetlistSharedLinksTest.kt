/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.setlists

import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.domain.implementation.RecordingSharedLinksRepository
import com.pandulapeter.campfire.domain.implementation.SetlistRepositoryStub
import com.pandulapeter.campfire.domain.implementation.testSetlist
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Only the device that renamed or deleted a setlist can tell its shares which it was. */
class SetlistSharedLinksTest {

    private val sharedLinks = RecordingSharedLinksRepository()

    @Test
    fun `a new title that renames the file is followed by its link`() = runTest {
        edit(renamedTo = "summer_gig.setlist.json", title = "Summer gig")

        assertEquals(listOf("followRename(setlist, gig.setlist.json, summer_gig.setlist.json)"), sharedLinks.noted)
    }

    @Test
    fun `an edit that keeps the file name, or writes nothing, tells the links nothing`() = runTest {
        edit(renamedTo = "gig.setlist.json", title = "Gig")
        edit(renamedTo = null, title = "Summer gig")

        assertEquals(emptyList(), sharedLinks.noted)
    }

    @Test
    fun `a deleted setlist stops being shared`() = runTest {
        DeleteSetlistUseCaseImpl(
            setlistRepository = object : SetlistRepositoryStub() {
                override suspend fun deleteSetlist(fileName: String) = Unit
            },
            sharedLinksRepository = sharedLinks,
        ).invoke("gig.setlist.json")

        assertEquals(listOf("stopSharingDeleted(setlist, gig.setlist.json)"), sharedLinks.noted)
    }

    @Test
    fun `a setlist that could not be deleted keeps being shared`() = runTest {
        val useCase = DeleteSetlistUseCaseImpl(
            setlistRepository = object : SetlistRepositoryStub() {
                override suspend fun deleteSetlist(fileName: String) = throw IllegalStateException("The file could not be deleted.")
            },
            sharedLinksRepository = sharedLinks,
        )

        assertFailsWith<IllegalStateException> { useCase.invoke("gig.setlist.json") }
        assertEquals(emptyList(), sharedLinks.noted)
    }

    private suspend fun edit(renamedTo: String?, title: String) = EditSetlistUseCaseImpl(
        setlistRepository = object : SetlistRepositoryStub() {
            override suspend fun renameSetlist(fileName: String, title: String, description: String, date: LocalDate, isCountdownShown: Boolean): Setlist? =
                renamedTo?.let { testSetlist(it, title = title) }
        },
        sharedLinksRepository = sharedLinks,
    ).invoke(fileName = "gig.setlist.json", title = title, description = "", date = LocalDate(2026, 10, 21), isCountdownShown = false)
}
