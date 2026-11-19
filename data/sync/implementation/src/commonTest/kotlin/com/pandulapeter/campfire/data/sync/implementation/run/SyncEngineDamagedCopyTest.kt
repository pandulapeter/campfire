/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.run

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.library.LibraryCollections
import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLock
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.sync.implementation.ACCOUNT_ID
import com.pandulapeter.campfire.data.sync.implementation.FakeLibraryFileLocalSource
import com.pandulapeter.campfire.data.sync.implementation.FakeSyncProvider
import com.pandulapeter.campfire.data.sync.implementation.LibrarySyncRules
import com.pandulapeter.campfire.data.sync.implementation.NoSetlistComparison
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import com.pandulapeter.campfire.data.sync.implementation.index.SyncIndexDocument
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** A copy in the folder that is not what its name says, met by one engine over several runs, as the app's one engine meets it. */
class SyncEngineDamagedCopyTest {

    private val local = FakeLibraryFileLocalSource()
    private val cloud = FakeSyncProvider()
    private val engine = SyncEngine(
        libraryFileLocalSource = local,
        libraryFileLock = LibraryFileLock(),
        references = LibrarySyncRules.references,
        conflictPolicies = LibrarySyncRules.conflictPolicies(NoSetlistComparison),
        plantedContentHash = { null },
        logger = Logger.Standard,
        collections = LibraryCollections.all,
        largeFileThreshold = LibraryCollections.LARGE_FILE_THRESHOLD,
        imageWritesGate = { it(heldByEditor) },
    )
    private var heldByEditor = emptySet<String>()
    private var index = SyncIndexDocument()

    @Test
    fun `a damaged copy is downloaded once while it does not change and a repaired one on the next run`() = runTest {
        local.files[SONG] = SONG_BYTES
        cloud.files[PHOTO] = DAMAGED to "r1"
        repeat(3) { assertEquals(listOf(PHOTO.name), run().summary.failed) }

        assertEquals(1, cloud.downloadCounts[PHOTO])
        assertNull(local.files[PHOTO])

        cloud.files[PHOTO] = PHOTO_BYTES to "r2"
        run()

        assertEquals(2, cloud.downloadCounts[PHOTO])
        assertContentEquals(PHOTO_BYTES, local.files[PHOTO])
    }

    @Test
    fun `a damaged copy no song here names is left in the folder without a failure`() = runTest {
        cloud.files[PHOTO] = DAMAGED to "r1"
        repeat(3) { assertEquals(emptyList(), run().summary.failed) }

        assertEquals(1, cloud.downloadCounts[PHOTO])
        assertNull(local.files[PHOTO])
        assertContentEquals(DAMAGED, cloud.files[PHOTO]?.first)
    }

    @Test
    fun `a damaged copy the editor's unsaved text names is a failure`() = runTest {
        heldByEditor = setOf(PHOTO.name)
        cloud.files[PHOTO] = DAMAGED to "r1"

        assertEquals(listOf(PHOTO.name), run().summary.failed)
        assertEquals(listOf(PHOTO.name), run().summary.failed)
    }

    private suspend fun run(): SyncEngine.Result.Completed {
        val result = assertIs<SyncEngine.Result.Completed>(
            engine.synchronize(
                provider = cloud,
                document = index,
                accountId = ACCOUNT_ID,
                onProgress = {},
                onIndexChanged = {},
                onLocalFileChanged = {},
                deletionPolicy = SyncDeletionPolicy.ASK,
            ),
        )
        index = result.index
        return result
    }

    private companion object {
        val PHOTO_BYTES = "photo".encodeToByteArray()
        val PHOTO = SyncKey(kind = LibraryCollections.FILES, name = localContentHash(PHOTO_BYTES).take(16) + ".png")
        val DAMAGED = "not the photo".encodeToByteArray()
        val SONG = SyncKey(kind = LibraryCollections.SONGS, name = "artist-title.cho")
        val SONG_BYTES = "{image: src=${PHOTO.name}}".encodeToByteArray()
    }
}
