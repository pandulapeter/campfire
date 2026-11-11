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

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The app killed partway through each multi-step write of the library and the preferences, at every step in turn, and
 * then started again over whatever reached the disk. Each step is one write or deletion of the real [JvmFileStorage],
 * which is atomic on its own (a temporary file moved into place), so a process that dies inside one leaves that file
 * whole, old or new; what can go wrong is the order of the steps, which is what is enumerated here.
 */
class InterruptedWriteTest {

    @Test
    fun `a song renamed by a run that dies at any step is still in the library on the next start`() = forEveryKillPoint(
        seed = { it.writeText(StorageDirectory.SONGS, "Old name.cho", SONG) },
        act = { storage -> SongLocalSourceImpl(storage, Logger.Standard).run { renameSong(loadSong("Old name.cho")!!) } },
        check = { storage ->
            val songs = SongLocalSourceImpl(storage, Logger.Standard)
            val texts = songs.loadSongs { }.map { songs.loadSongContent(it.fileName)?.text }
            assertTrue(SONG in texts, "The song is gone: $texts")
            assertTrue(texts.all { it == SONG } && texts.size <= 2, "More than the old and the new copy: $texts")
        },
    )

    @Test
    fun `a setlist renamed by a run that dies at any step is still listed whole on the next start`() = forEveryKillPoint(
        seed = { SetlistLocalSourceImpl(it, Logger.Standard).saveSetlist(SETLIST) },
        act = { SetlistLocalSourceImpl(it, Logger.Standard).renameSetlist(SETLIST, "Winter") },
        check = { storage ->
            val listed = SetlistLocalSourceImpl(storage, Logger.Standard).loadSetlists().map { it.setlist }
            assertTrue(listed.size in 1..2, "Not the old or the new setlist alone, or both: $listed")
            listed.forEach { assertEquals(SETLIST.entries, it.entries) }
        },
    )

    @Test
    fun `preferences saved by a run that dies at any step read as the old or the new ones on the next start`() = forEveryKillPoint(
        seed = { UserPreferencesLocalSourceImpl(it, Logger.Standard).run { saveUserPreferences(loadUserPreferences().copy(transpositions = OLD)) } },
        act = { UserPreferencesLocalSourceImpl(it, Logger.Standard).run { saveUserPreferences(loadUserPreferences().copy(transpositions = NEW)) } },
        check = { storage ->
            val transpositions = UserPreferencesLocalSourceImpl(storage, Logger.Standard).loadUserPreferences().transpositions
            assertTrue(transpositions == OLD || transpositions == NEW, "$transpositions")
        },
    )

    /**
     * Runs [act] over a library [seed] wrote, once dying before each of its writes in turn and once to its end, each
     * time in a library of its own, and then [check]s that library over a storage opened anew on what was left, as the
     * next launch would.
     */
    private fun forEveryKillPoint(
        seed: suspend (FileStorage) -> Unit,
        act: suspend (FileStorage) -> Unit,
        check: suspend (FileStorage) -> Unit,
    ) = runBlocking {
        var killPoint = 0
        do {
            val root = Files.createTempDirectory("campfire-interrupted").toFile()
            val hasDied = try {
                seed(JvmFileStorage(root))
                val hasDied = try {
                    act(DyingFileStorage(JvmFileStorage(root), writesBeforeDeath = killPoint))
                    false
                } catch (_: ProcessDeath) {
                    true
                }
                check(JvmFileStorage(root))
                hasDied
            } finally {
                root.deleteRecursively()
            }
            killPoint++
        } while (hasDied)
    }

    private class ProcessDeath : Error()

    /**
     * A storage whose process dies before its [writesBeforeDeath]+1st write or deletion: nothing after that point
     * reaches the disk, and no handler of the code under test runs, since an [Error] is no `Exception` it catches.
     */
    private class DyingFileStorage(private val delegate: FileStorage, private val writesBeforeDeath: Int) : FileStorage by delegate {

        private var writes = 0

        private fun step() {
            if (writes++ == writesBeforeDeath) throw ProcessDeath()
        }

        override suspend fun writeText(directory: StorageDirectory, name: String, text: String) {
            step()
            delegate.writeText(directory, name, text)
        }

        override suspend fun writeBytes(directory: StorageDirectory, name: String, bytes: ByteArray) {
            step()
            delegate.writeBytes(directory, name, bytes)
        }

        override suspend fun delete(directory: StorageDirectory, name: String) {
            step()
            delegate.delete(directory, name)
        }
    }

    private companion object {
        const val SONG = "{title: New name}\n{artist: Someone}\n[Am]Words\n"
        val SETLIST = Setlist(
            fileName = "summer.setlist.json",
            title = "Summer",
            description = "",
            date = LocalDate(2026, 1, 1),
            isArchived = false,
            entries = listOf(Setlist.Entry(songFileName = "a.cho")),
            size = 0L,
        )
        val OLD = mapOf("a.cho" to 1)
        val NEW = mapOf("a.cho" to 2)
    }
}
