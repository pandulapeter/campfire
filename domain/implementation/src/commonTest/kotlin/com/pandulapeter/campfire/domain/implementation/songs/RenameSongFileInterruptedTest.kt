/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.songs

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.domain.implementation.SetlistRepositoryStub
import com.pandulapeter.campfire.domain.implementation.SharedLinksRepositoryStub
import com.pandulapeter.campfire.domain.implementation.SongRepositoryStub
import com.pandulapeter.campfire.domain.implementation.TEST_PREFERENCES
import com.pandulapeter.campfire.domain.implementation.UserPreferencesRepositoryStub
import com.pandulapeter.campfire.domain.implementation.testSetlist
import com.pandulapeter.campfire.domain.implementation.testSong
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A rename is several writes to separate files - the song, every setlist naming it, the preferences, the shared links -
 * and the process can be killed between any two of them. Whatever it was killed before, the next launch must find every
 * reference naming a file that is there.
 */
class RenameSongFileInterruptedTest {

    @Test
    fun `a rename killed before any of its writes leaves every reference on a song that is there`() = runTest {
        val writeCount = Disk(writesLeft = Int.MAX_VALUE).also { rename(it) }.writes
        assertTrue(writeCount > 1)

        (0..writeCount).forEach { writesLeft ->
            val disk = Disk(writesLeft)
            try {
                rename(disk)
            } catch (_: ProcessKilled) {
                // What a kill leaves on disk is what the next launch reads.
            }

            val killedBefore = "killed before write ${writesLeft + 1} of $writeCount"
            assertTrue("a.cho" in disk.songs || "c.cho" in disk.songs, "The song is gone, $killedBefore.")
            disk.setlists.forEach { (setlist, entries) ->
                entries.forEach { assertTrue(it in disk.songs, "$setlist names the missing $it, $killedBefore.") }
            }
            disk.transpositions.keys.forEach { assertTrue(it in disk.songs, "The transposition of the missing $it is kept, $killedBefore.") }
            assertTrue(disk.sharedSong in disk.songs, "The shared link names the missing ${disk.sharedSong}, $killedBefore.")
        }
    }

    @Test
    fun `a rename that is not killed leaves the song under its new name alone`() = runTest {
        val disk = Disk(writesLeft = Int.MAX_VALUE)

        rename(disk)

        assertEquals(setOf("b.cho", "c.cho"), disk.songs)
        assertEquals(listOf("c.cho", "b.cho"), disk.setlists.getValue("first.setlist.json"))
        assertEquals(mapOf("c.cho" to 2), disk.transpositions)
        assertEquals("c.cho", disk.sharedSong)
    }

    private suspend fun rename(disk: Disk) = RenameSongFileUseCaseImpl(
        songRepository = DiskSongRepository(disk),
        setlistRepository = DiskSetlistRepository(disk),
        userPreferencesRepository = DiskUserPreferencesRepository(disk),
        sharedLinksRepository = DiskSharedLinksRepository(disk),
        logger = Logger.Standard,
    ).invoke(testSong("a.cho"))

    /** Not an exception: nothing in the app catches the end of its process. */
    private class ProcessKilled : Error("The process was killed.")

    /** What is stored, which dies before the write after the first [writesLeft] and refuses everything from then on. */
    private class Disk(private var writesLeft: Int) {
        val songs = mutableSetOf("a.cho", "b.cho")
        val setlists = mutableMapOf(
            "first.setlist.json" to listOf("a.cho", "b.cho"),
            "second.setlist.json" to listOf("b.cho", "a.cho"),
        )
        var transpositions = mapOf("a.cho" to 2)
        var sharedSong = "a.cho"
        var writes = 0
            private set
        private var isDead = false

        fun read() {
            if (isDead) throw ProcessKilled()
        }

        fun write(change: () -> Unit) {
            read()
            if (writesLeft == 0) {
                isDead = true
                throw ProcessKilled()
            }
            writesLeft--
            writes++
            change()
        }
    }

    private class DiskSongRepository(private val disk: Disk) : SongRepositoryStub() {
        override suspend fun copySongToItsName(song: Song): Song {
            disk.write { disk.songs += "c.cho" }
            return testSong("c.cho")
        }

        override suspend fun finishSongRename(fileName: String, renamed: Song, shouldKeepOldFile: Boolean) {
            if (!shouldKeepOldFile) disk.write { disk.songs -= fileName }
        }
    }

    private class DiskSetlistRepository(private val disk: Disk) : SetlistRepositoryStub() {
        override suspend fun loadSetlistFileNamesNaming(songFileName: String): List<String> {
            disk.read()
            return disk.setlists.filterValues { songFileName in it }.keys.toList()
        }

        override suspend fun updateSetlist(fileName: String, transform: (Setlist) -> Setlist): Setlist? {
            val entries = disk.setlists[fileName] ?: return null
            val updated = transform(testSetlist(fileName, entries = entries))
            disk.write { disk.setlists[fileName] = updated.entries.map { it.songFileName } }
            return updated
        }
    }

    private class DiskUserPreferencesRepository(private val disk: Disk) : UserPreferencesRepositoryStub() {
        override suspend fun loadUserPreferencesIfNeeded(): UserPreferences {
            disk.read()
            return TEST_PREFERENCES.copy(transpositions = disk.transpositions)
        }

        override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences) {
            val updated = transform(loadUserPreferencesIfNeeded())
            disk.write { disk.transpositions = updated.transpositions }
        }
    }

    private class DiskSharedLinksRepository(private val disk: Disk) : SharedLinksRepositoryStub() {
        override suspend fun followRename(kind: SharedLinkKind, fileName: String, newFileName: String) {
            if (disk.sharedSong == fileName) disk.write { disk.sharedSong = newFileName }
        }
    }
}
