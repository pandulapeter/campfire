/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.library

import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.domain.implementation.SetlistRepositoryStub
import com.pandulapeter.campfire.domain.implementation.SongRepositoryStub
import com.pandulapeter.campfire.domain.implementation.TEST_PREFERENCES
import com.pandulapeter.campfire.domain.implementation.UserPreferencesRepositoryStub
import com.pandulapeter.campfire.domain.implementation.testSetlist
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoadScreenDataUseCaseImplTest {

    private val calls = mutableListOf<String>()

    /** Lets a song read wait until a test opens it, to show the other reads do not wait for it. */
    private var songGate: CompletableDeferred<Unit>? = null

    private val songs = object : SongRepositoryStub() {
        override suspend fun loadSongsIfNeeded(): List<Song>? {
            songGate?.await()
            calls += "songs loaded"
            return null
        }

        override suspend fun rescan() {
            calls += "songs rescanned"
        }
    }

    private val setlists = object : SetlistRepositoryStub() {
        override suspend fun loadSetlistsIfNeeded(): List<Setlist> {
            calls += "setlists loaded"
            return listOf(testSetlist("set.setlist.json", entries = listOf("a.cho")))
        }

        override suspend fun rescan() {
            calls += "setlists rescanned"
        }
    }

    private val preferences = object : UserPreferencesRepositoryStub() {
        override suspend fun loadUserPreferencesIfNeeded(): UserPreferences {
            calls += "preferences loaded"
            return TEST_PREFERENCES
        }
    }

    private val loadScreenData = LoadScreenDataUseCaseImpl(
        setlistRepository = setlists,
        songRepository = songs,
        userPreferencesRepository = preferences,
    )

    @Test
    fun `a first load reads what is not loaded yet`() = runTest {
        loadScreenData(isRescan = false)
        assertEquals(setOf("songs loaded", "setlists loaded", "preferences loaded"), calls.toSet())
    }

    @Test
    fun `a rescan reads the library again and the preferences only if needed`() = runTest {
        loadScreenData(isRescan = true)
        assertEquals(setOf("songs rescanned", "setlists rescanned", "preferences loaded"), calls.toSet())
    }

    @Test
    fun `a source that fails or takes long does not keep the others from loading`() = runTest {
        val gate = CompletableDeferred<Unit>().also { songGate = it }
        val load = launch { loadScreenData(isRescan = false) }
        runCurrent()
        assertEquals(setOf("setlists loaded", "preferences loaded"), calls.toSet())
        assertFalse(load.isCompleted)
        gate.complete(Unit)
        load.join()
        assertTrue("songs loaded" in calls)
    }
}
