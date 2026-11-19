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

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.library.LibraryCollections
import com.pandulapeter.campfire.data.model.song.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LibrarySyncObserverTest {

    private val songRepository = RecordingSongRepository()
    private val setlistRepository = RecordingSetlistRepository()
    private val imageRepository = RecordingImageRepository()
    private val observer = LibrarySyncObserver(songRepository, setlistRepository, imageRepository)

    @Test
    fun `a refresh hands each repository the names of its own files`() = runTest {
        observer.refresh(
            setOf(
                SyncKey(kind = LibraryCollections.SONGS, name = "a.cho"),
                SyncKey(kind = LibraryCollections.SETLISTS, name = "b.setlist.json"),
            ),
        )

        assertEquals(listOf("a.cho"), songRepository.refreshed)
        assertEquals(listOf("b.setlist.json"), setlistRepository.refreshed)
        assertEquals(0, imageRepository.changeCount)
    }

    @Test
    fun `a refresh with a library file tells the pictures`() = runTest {
        observer.refresh(setOf(SyncKey(kind = LibraryCollections.FILES, name = "0123456789abcdef.png")))

        assertEquals(emptyList(), songRepository.refreshed)
        assertEquals(1, imageRepository.changeCount)
    }

    @Test
    fun `a rescan reads every part of the library again`() = runTest {
        observer.rescan()

        assertEquals(1, songRepository.rescanCount)
        assertEquals(1, setlistRepository.rescanCount)
        assertEquals(1, imageRepository.changeCount)
    }

    @Test
    fun `the first read is waited for only while the songs are still loading`() = runTest {
        var loads = 0
        val loading = MutableStateFlow<DataState<List<Song>>>(DataState.Loading(emptyList()))
        LibrarySyncObserver(RecordingSongRepository(songs = loading, onLoadIfNeeded = { loads++ }), setlistRepository, imageRepository)
            .awaitFirstRead()
        observer.awaitFirstRead()

        assertEquals(1, loads)
    }
}
