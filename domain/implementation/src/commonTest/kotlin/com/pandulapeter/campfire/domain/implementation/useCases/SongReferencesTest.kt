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
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.models.SongFileRename
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The walk a rename and a deletion make once the song's file has moved or gone: every reference is attempted whatever
 * fails, since the file cannot be put back, and the answer is whether all of them followed.
 */
class SongReferencesTest {

    private val setlists = FakeSetlistRepository(
        listOf(
            testSetlist("first.setlist.json", entries = listOf("a.cho", "b.cho")),
            testSetlist("second.setlist.json", entries = listOf("b.cho", "a.cho")),
            testSetlist("other.setlist.json", entries = listOf("b.cho")),
        ),
    )
    private val preferences = FakeUserPreferencesRepository(mapOf("a.cho" to 2, "b.cho" to 1))

    @Test
    fun `a rename moves every entry and the transposition`() = runTest {
        assertTrue(follow(newFileName = "c.cho"))

        assertEquals(listOf("c.cho", "b.cho"), setlists.entriesOf("first.setlist.json"))
        assertEquals(listOf("b.cho", "c.cho"), setlists.entriesOf("second.setlist.json"))
        assertEquals(listOf("b.cho"), setlists.entriesOf("other.setlist.json"))
        assertEquals(mapOf("b.cho" to 1, "c.cho" to 2), preferences.transpositions)
    }

    @Test
    fun `a deletion drops every entry and the transposition`() = runTest {
        assertTrue(follow(newFileName = null))

        assertEquals(listOf("b.cho"), setlists.entriesOf("first.setlist.json"))
        assertEquals(listOf("b.cho"), setlists.entriesOf("second.setlist.json"))
        assertEquals(mapOf("b.cho" to 1), preferences.transpositions)
    }

    @Test
    fun `setlists that cannot be listed are a failure and the transposition still follows`() = runTest {
        setlists.isListingBroken = true

        assertFalse(follow(newFileName = "c.cho"))
        assertEquals(mapOf("b.cho" to 1, "c.cho" to 2), preferences.transpositions)

        assertFalse(follow(newFileName = null, fileName = "c.cho"))
        assertEquals(mapOf("b.cho" to 1), preferences.transpositions)
    }

    @Test
    fun `a setlist that cannot be written does not stop the rest`() = runTest {
        setlists.unwritable = setOf("first.setlist.json")

        assertFalse(follow(newFileName = null))

        assertEquals(listOf("a.cho", "b.cho"), setlists.entriesOf("first.setlist.json"))
        assertEquals(listOf("b.cho"), setlists.entriesOf("second.setlist.json"))
        assertEquals(mapOf("b.cho" to 1), preferences.transpositions)
    }

    @Test
    fun `the folded sections follow a rename and go with a deletion`() = runTest {
        val folded = FakeUserPreferencesRepository(transpositions = emptyMap(), foldedSections = mapOf("a.cho" to setOf("chorus#1"), "b.cho" to setOf("verse#2")))

        assertTrue(followSongReferences(setlistRepository = setlists, userPreferencesRepository = folded, logger = Logger.Standard, fileName = "a.cho", newFileName = "c.cho"))
        assertEquals(mapOf("b.cho" to setOf("verse#2"), "c.cho" to setOf("chorus#1")), folded.foldedSections)

        assertTrue(followSongReferences(setlistRepository = setlists, userPreferencesRepository = folded, logger = Logger.Standard, fileName = "c.cho", newFileName = null))
        assertEquals(mapOf("b.cho" to setOf("verse#2")), folded.foldedSections)
    }

    @Test
    fun `a setlist that went away is nothing left to follow`() = runTest {
        setlists.gone = setOf("first.setlist.json")

        assertTrue(follow(newFileName = null))
        assertEquals(listOf("b.cho"), setlists.entriesOf("second.setlist.json"))
    }

    @Test
    fun `a deletion answers what the walk answered`() = runTest {
        setlists.unwritable = setOf("first.setlist.json")
        val songs = FakeSongRepository()

        assertFalse(DeleteSongUseCaseImpl(songs, setlists, preferences, Logger.Standard).invoke("a.cho"))
        assertEquals(listOf("a.cho"), songs.deleted)
    }

    @Test
    fun `a song that could not be deleted is not walked`() = runTest {
        val songs = FakeSongRepository(isBroken = true)

        assertFailsWith<IllegalStateException> { DeleteSongUseCaseImpl(songs, setlists, preferences, Logger.Standard).invoke("a.cho") }
        assertEquals(listOf("a.cho", "b.cho"), setlists.entriesOf("first.setlist.json"))
        assertEquals(mapOf("a.cho" to 2, "b.cho" to 1), preferences.transpositions)
    }

    @Test
    fun `a rename answers the name the file moved to and whether the references followed it`() = runTest {
        val rename = RenameSongFileUseCaseImpl(FakeSongRepository(renamedTo = "c.cho"), setlists, preferences, Logger.Standard).invoke(testSong("a.cho"))

        assertEquals(SongFileRename(fileName = "c.cho", haveReferencesFollowed = true), rename)
        assertEquals(listOf("c.cho", "b.cho"), setlists.entriesOf("first.setlist.json"))
    }

    @Test
    fun `a file that did not move leaves every reference where it was`() = runTest {
        val rename = RenameSongFileUseCaseImpl(FakeSongRepository(renamedTo = null), setlists, preferences, Logger.Standard).invoke(testSong("a.cho"))

        assertNull(rename)
        assertEquals(listOf("a.cho", "b.cho"), setlists.entriesOf("first.setlist.json"))
        assertEquals(mapOf("a.cho" to 2, "b.cho" to 1), preferences.transpositions)
    }

    private suspend fun follow(newFileName: String?, fileName: String = "a.cho") = followSongReferences(
        setlistRepository = setlists,
        logger = Logger.Standard,
        userPreferencesRepository = preferences,
        fileName = fileName,
        newFileName = newFileName,
    )

    private class FakeSetlistRepository(setlists: List<Setlist>) : SetlistRepositoryStub() {
        private val files = setlists.associateByTo(mutableMapOf()) { it.fileName }
        var isListingBroken = false
        var unwritable = emptySet<String>()

        /** Listed, but deleted by the time the walk asks to change them. */
        var gone = emptySet<String>()

        fun entriesOf(fileName: String) = files.getValue(fileName).entries.map { it.songFileName }

        override suspend fun loadSetlistFileNamesNaming(songFileName: String): List<String> {
            if (isListingBroken) throw IllegalStateException("The setlists could not be listed.")
            return files.values.filter { setlist -> setlist.entries.any { it.songFileName == songFileName } }.map { it.fileName }
        }

        override suspend fun updateSetlist(fileName: String, transform: (Setlist) -> Setlist): Setlist? {
            if (fileName in unwritable) throw IllegalStateException("The setlist could not be written.")
            if (fileName in gone) return null
            return files[fileName]?.let(transform)?.also { files[fileName] = it }
        }
    }

    private class FakeUserPreferencesRepository(
        var transpositions: Map<String, Int>,
        var foldedSections: Map<String, Set<String>> = emptyMap(),
    ) : UserPreferencesRepositoryStub() {
        override suspend fun loadUserPreferencesIfNeeded() = TEST_PREFERENCES.copy(transpositions = transpositions, foldedSections = foldedSections)

        override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences) {
            transform(loadUserPreferencesIfNeeded()).let { updated ->
                transpositions = updated.transpositions
                foldedSections = updated.foldedSections
            }
        }
    }

    /** @param renamedTo Where a rename moves a song, null for a file that could not be moved. */
    private class FakeSongRepository(private val isBroken: Boolean = false, private val renamedTo: String? = null) : SongRepositoryStub() {
        val deleted = mutableListOf<String>()

        override suspend fun deleteSong(fileName: String) {
            if (isBroken) throw IllegalStateException("The file could not be deleted.")
            deleted += fileName
        }

        override suspend fun renameSong(song: Song) = renamedTo?.let(::testSong)
    }
}
