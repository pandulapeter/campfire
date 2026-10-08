/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation.sync

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PassListingTest {

    @Test
    fun `a file too large to read is left out on both sides with its index entry`() {
        val pass = prepare(index = mapOf(song("a") to ENTRY), listed = listOf(remote("a")), tooLarge = setOf(song("a")))

        assertTrue(pass.remote.isEmpty())
        assertTrue(pass.index.isEmpty())
    }

    @Test
    fun `an unreadable file keeps its index entry but is not planned with`() {
        val pass = prepare(index = mapOf(song("a") to ENTRY), listed = listOf(remote("a")), unreadable = setOf(song("a")))

        assertEquals(setOf(song("a")), pass.index.keys)
        assertTrue(pass.planningIndex.isEmpty())
        assertTrue(pass.remote.isEmpty())
    }

    @Test
    fun `a remote name the device cannot hold is reported and never folded onto a local one`() {
        val pass = prepare(
            local = listOf(local("what")),
            listed = listOf(remote("What?")),
            canHoldFileName = { _, name -> '?' !in name },
        )

        assertEquals(listOf("What?.cho"), pass.unstorable)
        assertTrue(pass.remote.isEmpty())
    }

    @Test
    fun `a remote spelling takes the local one`() {
        val pass = prepare(local = listOf(local("song")), listed = listOf(remote("Song")))

        assertEquals(listOf(song("song")), pass.remote.map { it.key })
    }

    @Test
    fun `an orphaned index entry follows the listed spelling`() {
        val pass = prepare(index = mapOf(song("Song") to ENTRY), local = listOf(local("song")))

        assertEquals(setOf(song("song")), pass.index.keys)
    }

    @Test
    fun `two local names that differ only in case collide`() {
        val pass = prepare(local = listOf(local("song"), local("Song"), local("other")))

        assertEquals(setOf(song("song"), song("Song")), pass.caseCollisions)
    }

    @Test
    fun `keeping and uploading forgets the files only here and their preferences`() {
        val pass = prepare(
            index = mapOf(song("a") to ENTRY, song("b") to ENTRY),
            local = listOf(local("a"), local("b")),
            listed = listOf(remote("b")),
            deletionPolicy = SyncDeletionPolicy.KEEP_AND_UPLOAD,
            syncedPreferences = preferences("A.cho", "b.cho"),
        )

        assertEquals(setOf(song("b")), pass.index.keys)
        assertEquals(setOf("b.cho"), SyncedPreferencesDocument.songNamesOf(pass.syncedPreferences))
    }

    @Test
    fun `keeping and downloading forgets the files only there`() {
        val pass = prepare(
            index = mapOf(song("a") to ENTRY, song("b") to ENTRY),
            local = listOf(local("b")),
            listed = listOf(remote("a"), remote("b")),
            deletionPolicy = SyncDeletionPolicy.KEEP_AND_DOWNLOAD,
        )

        assertEquals(setOf(song("b")), pass.index.keys)
    }

    @Test
    fun `a remote file that is not a library file is left out`() {
        val pass = prepare(listed = listOf(RemoteFile(kind = LibraryFileKind.SONG, name = "notes.txt", revision = "r", contentHash = null, size = 0)))

        assertTrue(pass.remote.isEmpty())
        assertTrue(pass.unstorable.isEmpty())
    }

    private fun prepare(
        index: Map<SyncKey, SyncIndexEntry> = emptyMap(),
        listed: List<RemoteFile> = emptyList(),
        local: List<LocalFileState> = emptyList(),
        tooLarge: Set<SyncKey> = emptySet(),
        unreadable: Set<SyncKey> = emptySet(),
        canHoldFileName: (LibraryFileKind, String) -> Boolean = { _, _ -> true },
        deletionPolicy: SyncDeletionPolicy = SyncDeletionPolicy.ASK,
        syncedPreferences: JsonObject? = null,
    ) = preparePass(
        index = index,
        listed = listed,
        local = local,
        tooLarge = tooLarge,
        unreadable = unreadable,
        canHoldFileName = canHoldFileName,
        deletionPolicy = deletionPolicy,
        syncedPreferences = syncedPreferences,
    )

    private fun song(name: String) = SyncKey(kind = LibraryFileKind.SONG, name = "$name.cho")

    private fun local(name: String) = LocalFileState(key = song(name), hash = "h")

    private fun remote(name: String) = RemoteFile(kind = LibraryFileKind.SONG, name = "$name.cho", revision = "r", contentHash = null, size = 0)

    private fun preferences(vararg songs: String) = Json.parseToJsonElement(
        """{"version": 1, "songs": {${songs.joinToString { "\"$it\": {\"tempo\": 90}" }}}}""",
    ).jsonObject

    private companion object {
        val ENTRY = SyncIndexEntry(localHash = "h", remoteRevision = "r")
    }
}
