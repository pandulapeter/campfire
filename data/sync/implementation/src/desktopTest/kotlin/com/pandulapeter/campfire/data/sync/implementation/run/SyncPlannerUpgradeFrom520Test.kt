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

import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.sync.implementation.FakeSyncIndexLocalSource
import com.pandulapeter.campfire.data.sync.implementation.LocalFileState
import com.pandulapeter.campfire.data.sync.implementation.RemoteFileState
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import com.pandulapeter.campfire.data.sync.implementation.index.SyncIndexStore
import com.pandulapeter.campfire.data.sync.implementation.testEnvironment
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Plans a run from the sync index Campfire 5.2.0 left behind after a run that completed (`resources/upgrade-5.2.0`,
 * written by that version's own index store over the library beside it), so that an upgrade that changes how a file is
 * keyed or hashed, or how the index is read, shows up here as a run that would copy or delete files nobody touched.
 */
class SyncPlannerUpgradeFrom520Test {

    private val fixture = File(requireNotNull(javaClass.getResource("/upgrade-5.2.0")).toURI())

    @Test
    fun `the index 5_2_0 left behind reads as the library it was written for`() = runTest {
        val document = SyncIndexStore(FakeSyncIndexLocalSource(index = indexText()), testEnvironment()).load()

        assertEquals("dropbox", document.providerId)
        assertEquals("dropbox:dbid:AAH4f99T0taONIb-OurWxbNQ6ywGRopQngc", document.accountId)
        assertEquals(1_761_000_000_000, document.lastSyncedAt)
        assertFalse(document.isRunInProgress)
        assertEquals(localFiles().map { it.key }.toSet(), document.toIndex().keys)
    }

    @Test
    fun `a run planned from the 5_2_0 index over an unchanged library and remote changes nothing on either side`() = runTest {
        val index = SyncIndexStore(FakeSyncIndexLocalSource(index = indexText()), testEnvironment()).load().toIndex()
        val remote = index.map { (key, entry) -> RemoteFileState(key = key, revision = entry.remoteRevision, contentHash = null, size = 1) }

        val plan = SyncPlanner.plan(local = localFiles(), remote = remote, index = index)

        assertTrue(index.isNotEmpty())
        assertEquals(emptyList(), plan)
    }

    private fun indexText() = File(fixture, "preferences/sync-index.json").readText()

    private fun localFiles() = LibraryFileKind.entries.flatMap { kind ->
        requireNotNull(File(fixture, "library/${kind.id}").listFiles()).map { file ->
            LocalFileState(key = SyncKey(kind = kind, name = file.name), hash = localContentHash(file.readBytes()))
        }
    }
}
