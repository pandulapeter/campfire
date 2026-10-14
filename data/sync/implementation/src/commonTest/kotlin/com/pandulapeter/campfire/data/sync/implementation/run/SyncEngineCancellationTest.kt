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

import com.pandulapeter.campfire.data.sync.implementation.FakeLibraryFileLocalSource
import com.pandulapeter.campfire.data.sync.implementation.FakeSyncProvider
import com.pandulapeter.campfire.data.sync.implementation.HERE
import com.pandulapeter.campfire.data.sync.implementation.ORIGINAL
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import com.pandulapeter.campfire.data.sync.implementation.THERE
import com.pandulapeter.campfire.data.sync.implementation.index.SyncIndexDocument
import com.pandulapeter.campfire.data.sync.implementation.indexOf
import com.pandulapeter.campfire.data.sync.implementation.song
import com.pandulapeter.campfire.data.sync.implementation.synchronize
import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * A run stopped while it writes a library file: the write, once started, finishes, and the run still reports the file it
 * changed, so the library re-reads it rather than keeping the version it had before.
 */
class SyncEngineCancellationTest {

    @Test
    fun `a download stopped during its write still reports the file it wrote`() = runTest {
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to ORIGINAL))
        val provider = FakeSyncProvider(files = mapOf(song(1) to THERE))

        val changed = stopDuringTheWriteOf(song(1), local, provider, indexOf(song(1) to ORIGINAL))

        assertContentEquals(THERE, local.files[song(1)])
        assertTrue(song(1) in changed)
    }

    @Test
    fun `a conflict copy stopped during its write still reports the copy`() = runTest {
        val copy = SyncKey(kind = LibraryFileKind.SONG, name = "song_1 (2).cho")
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to HERE))
        val provider = FakeSyncProvider(files = mapOf(song(1) to THERE))

        val changed = stopDuringTheWriteOf(copy, local, provider, indexOf(song(1) to ORIGINAL))

        assertContentEquals(THERE, local.files[copy])
        assertTrue(copy in changed)
    }

    /** Runs a sync, stops it while the platform is writing [key], lets that write finish and answers what the run reported. */
    private suspend fun TestScope.stopDuringTheWriteOf(
        key: SyncKey,
        local: FakeLibraryFileLocalSource,
        provider: FakeSyncProvider,
        document: SyncIndexDocument,
    ): List<SyncKey> {
        val writeStarted = CompletableDeferred<Unit>()
        val writeMayFinish = CompletableDeferred<Unit>()
        local.onWrite = { written ->
            if (written == key) {
                writeStarted.complete(Unit)
                writeMayFinish.await()
            }
        }
        val changed = mutableListOf<SyncKey>()
        val run = launch { synchronize(local, provider, document, onLocalFileChanged = { changed += it }) }
        writeStarted.await()
        run.cancel()
        writeMayFinish.complete(Unit)
        run.join()
        return changed
    }
}
