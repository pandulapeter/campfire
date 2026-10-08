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

import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The deletion's own sync run carries the answer the user typed, and a run that is already going would keep it from
 * starting, so the order of what the use case asks for is what is pinned here.
 */
class DeleteLibraryUseCaseImplTest {

    private val events = mutableListOf<String>()
    private var savedPreferences: UserPreferences? = null

    @Test
    fun `stops the run going before deleting and starts one with the deletions allowed after`() = runTest {
        useCase().invoke()

        assertEquals(
            listOf(
                "cancelSynchronization",
                "deleteAllSongs",
                "deleteAllSetlists",
                "updateUserPreferences",
                "synchronize(DELETE_REMOTELY)",
            ),
            events,
        )
    }

    @Test
    fun `starts the run and throws when a song could not be deleted`() = runTest {
        val failure = IllegalStateException("The songs could not be deleted.")

        val thrown = assertFailsWith<IllegalStateException> { useCase(songsFailure = failure).invoke() }

        // Compared by message, since the coroutine machinery may hand back a copy that carries the stack it crossed.
        assertEquals(failure.message, thrown.message)
        assertEquals(
            listOf(
                "cancelSynchronization",
                "deleteAllSetlists",
                "updateUserPreferences",
                "synchronize(DELETE_REMOTELY)",
            ),
            events,
        )
    }

    @Test
    fun `clears the transpositions and the folded sections`() = runTest {
        useCase().invoke()

        assertEquals(emptyMap(), savedPreferences?.transpositions)
        assertEquals(emptyMap(), savedPreferences?.foldedSections)
    }

    private fun useCase(songsFailure: Exception? = null) = DeleteLibraryUseCaseImpl(
        songRepository = object : SongRepositoryStub() {
            override suspend fun deleteAllSongs() {
                songsFailure?.let { throw it }
                events += "deleteAllSongs"
            }
        },
        setlistRepository = object : SetlistRepositoryStub() {
            override suspend fun deleteAllSetlists() {
                events += "deleteAllSetlists"
            }
        },
        userPreferencesRepository = object : UserPreferencesRepositoryStub() {
            override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences) {
                events += "updateUserPreferences"
                savedPreferences = transform(
                    TEST_PREFERENCES.copy(transpositions = mapOf("foo.cho" to 2), foldedSections = mapOf("foo.cho" to setOf("Chorus"))),
                )
            }
        },
        syncRepository = object : SyncRepositoryStub() {
            override fun synchronize(deletionPolicy: SyncDeletionPolicy): Boolean {
                events += "synchronize($deletionPolicy)"
                return true
            }

            override fun cancelSynchronization() {
                events += "cancelSynchronization"
            }
        },
        logger = Logger.Standard,
    )
}
