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
import com.pandulapeter.campfire.data.sync.implementation.renamedIndexOf
import com.pandulapeter.campfire.data.sync.implementation.song
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A file a song names that neither side holds and the index does not know, asked back from the folder's history by one
 * engine over several runs, as the app's one engine asks for the life of the process.
 */
class SyncEngineRestoreTest {

    private val local = FakeLibraryFileLocalSource(mapOf(song(1) to SONG))
    private val cloud = FakeSyncProvider(mapOf(song(1) to SONG))
    private val engine = SyncEngine(
        libraryFileLocalSource = local,
        libraryFileLock = LibraryFileLock(),
        setlistComparison = NoSetlistComparison,
        plantedContentHash = { null },
        logger = Logger.Standard,
    )
    private var index = renamedIndexOf(song(1) to SONG, revision = "r1")

    @Test
    fun `a name the folder has no history of changes nothing and is asked for once`() = runTest {
        val first = run()
        run()

        assertFalse(first.asksForFollowUp)
        assertEquals(listOf(PHOTO), cloud.restoreRequests)
        assertEquals(setOf(song(1)), cloud.files.keys)
    }

    @Test
    fun `a file restored from the history is downloaded by the run that follows`() = runTest {
        cloud.deletedFiles[PHOTO] = PHOTO_BYTES

        assertTrue(run().asksForFollowUp)
        run()

        assertEquals(PHOTO_BYTES.decodeToString(), local.files[PHOTO]?.decodeToString())
    }

    @Test
    fun `a restored copy that is not what its name says is a damaged copy, never asked for again`() = runTest {
        cloud.deletedFiles[PHOTO] = "an older picture".encodeToByteArray()

        assertTrue(run().asksForFollowUp)
        val next = run()
        run()

        assertNull(local.files[PHOTO])
        assertEquals(listOf(PHOTO.name), next.summary.failed)
        assertEquals(listOf(PHOTO), cloud.restoreRequests)
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
        val SONG = "{image: src=${PHOTO.name}}".encodeToByteArray()
    }
}
