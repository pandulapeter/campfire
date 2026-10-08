/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `sync-index.json` as devices already have it on disk. The store reads it leniently, so a field renamed in
 * [SyncIndexDocument] would not fail to decode: it would read as its default, an empty index, and the next run would
 * bring back every file deleted since the last one. These documents are written out by hand for that reason, rather
 * than by the class they test.
 */
class SyncIndexDocumentTest {

    @Test
    fun `an index in the current format is read with every field`() = runTest {
        val document = load(
            """
            {
                "providerId": "dropbox",
                "accountId": "dropbox:dbid:1",
                "lastSyncedAt": 1760000000000,
                "isRunInProgress": true,
                "isAutomaticRunInProgress": true,
                "entries": {
                    "songs/song_1.cho": {
                        "localHash": "hash1",
                        "remoteRevision": "r1"
                    },
                    "setlists/Summer tour.setlist.json": {
                        "localHash": "hash2",
                        "remoteRevision": "r2"
                    }
                },
                "syncedPreferences": {
                    "version": 1
                }
            }
            """.trimIndent(),
        )

        assertEquals(
            SyncIndexDocument(
                providerId = "dropbox",
                accountId = "dropbox:dbid:1",
                lastSyncedAt = 1_760_000_000_000,
                isRunInProgress = true,
                isAutomaticRunInProgress = true,
                entries = mapOf(
                    "songs/song_1.cho" to SyncIndexDocument.Entry(localHash = "hash1", remoteRevision = "r1"),
                    "setlists/Summer tour.setlist.json" to SyncIndexDocument.Entry(localHash = "hash2", remoteRevision = "r2"),
                ),
                syncedPreferences = JsonObject(mapOf("version" to JsonPrimitive(1))),
            ),
            document,
        )
        assertEquals(
            mapOf(
                song(1) to SyncIndexEntry(localHash = "hash1", remoteRevision = "r1"),
                SyncKey(LibraryFileKind.SETLIST, "Summer tour.setlist.json") to SyncIndexEntry(localHash = "hash2", remoteRevision = "r2"),
            ),
            document.toIndex(),
        )
    }

    /** As the versions before automatic runs were marked and before the per-song preferences were synced wrote it. */
    @Test
    fun `an index written before the newer fields existed is read whole`() = runTest {
        val document = load(
            """
            {
                "providerId": "dropbox",
                "accountId": "dropbox:dbid:1",
                "lastSyncedAt": 42,
                "isRunInProgress": false,
                "entries": {
                    "songs/song_1.cho": {
                        "localHash": "hash1",
                        "remoteRevision": "r1"
                    }
                }
            }
            """.trimIndent(),
        )

        assertEquals(
            SyncIndexDocument(
                providerId = "dropbox",
                accountId = "dropbox:dbid:1",
                lastSyncedAt = 42,
                entries = mapOf("songs/song_1.cho" to SyncIndexDocument.Entry(localHash = "hash1", remoteRevision = "r1")),
            ),
            document,
        )
    }

    @Test
    fun `an index the store wrote is read back unchanged`() = runTest {
        val localSource = FakeSyncIndexLocalSource()
        val store = SyncIndexStore(localSource, testEnvironment())
        val document = SyncIndexDocument(
            providerId = "dropbox",
            accountId = "dropbox:dbid:1",
            lastSyncedAt = 42,
            isRunInProgress = true,
            isAutomaticRunInProgress = true,
            entries = mapOf("songs/song_1.cho" to SyncIndexDocument.Entry(localHash = "hash1", remoteRevision = "r1")),
            syncedPreferences = JsonObject(mapOf("version" to JsonPrimitive(1))),
        )

        store.save(document)

        assertEquals(document, store.load())
    }

    /** The index is keyed by a path, and a key has to survive the round trip through it unchanged. */
    @Test
    fun `a key survives being written to the index and read back`() {
        val key = SyncKey(LibraryFileKind.SETLIST, "Summer tour.setlist.json")
        assertEquals(expected = key, actual = SyncKey.fromPath(key.path))
    }

    @Test
    fun `a key with a slash in the name is not read back as something else`() =
        assertEquals(expected = null, actual = SyncKey.fromPath("nonsense"))

    private suspend fun TestScope.load(text: String) =
        SyncIndexStore(FakeSyncIndexLocalSource(index = text), testEnvironment()).load()
}
