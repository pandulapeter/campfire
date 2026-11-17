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
import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLock
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.sync.implementation.ACCOUNT_ID
import com.pandulapeter.campfire.data.sync.implementation.FakeLibraryFileLocalSource
import com.pandulapeter.campfire.data.sync.implementation.FakeSyncProvider
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
        setlistComparison = NoSetlistComparison,
        plantedContentHash = { null },
        logger = Logger.Standard,
    )
    private var index = SyncIndexDocument()

    @Test
    fun `a damaged copy is downloaded once while it does not change, and a repaired one on the next run`() = runTest {
        cloud.files[PHOTO] = "not the photo".encodeToByteArray() to "r1"
        repeat(3) { assertEquals(listOf(PHOTO.name), run().summary.failed) }

        assertEquals(1, cloud.downloadCounts[PHOTO])
        assertNull(local.files[PHOTO])

        cloud.files[PHOTO] = PHOTO_BYTES to "r2"
        run()

        assertEquals(2, cloud.downloadCounts[PHOTO])
        assertContentEquals(PHOTO_BYTES, local.files[PHOTO])
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
        val PHOTO = SyncKey(kind = LibraryFileKind.FILE, name = localContentHash(PHOTO_BYTES).take(16) + ".png")
    }
}
