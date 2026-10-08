/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.repository.implementation.sync.FakeLibraryFileLocalSource
import com.pandulapeter.campfire.data.repository.implementation.sync.FakeUserPreferencesRepository
import com.pandulapeter.campfire.data.repository.implementation.sync.SyncKey
import com.pandulapeter.campfire.data.repository.implementation.sync.defaultUserPreferences
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DemoLibraryRepositoryImplTest {

    @Test
    fun `rememberDemoLibraryFiles records the content of the files that are there`() = runTest {
        val a = "Song a".encodeToByteArray()
        val b = "Setlist b".encodeToByteArray()
        val libraryFileLocalSource = FakeLibraryFileLocalSource(
            files = mapOf(
                SyncKey(kind = LibraryFileKind.SONG, name = "a.cho") to a,
                SyncKey(kind = LibraryFileKind.SETLIST, name = "b.setlist.json") to b,
            ),
        )
        val userPreferencesRepository = FakeUserPreferencesRepository(
            defaultUserPreferences().copy(demoLibraryContentHashes = mapOf("songs/earlier.cho" to "0a1b")),
        )

        repository(libraryFileLocalSource, userPreferencesRepository)
            .rememberDemoLibraryFiles(songFileNames = listOf("a.cho", "missing.cho"), setlistFileNames = listOf("b.setlist.json"))

        assertEquals(
            mapOf(
                "songs/earlier.cho" to "0a1b",
                "songs/a.cho" to localContentHash(a),
                "setlists/b.setlist.json" to localContentHash(b),
            ),
            userPreferencesRepository.current.demoLibraryContentHashes,
        )
    }

    @Test
    fun `a file that is not there is not recorded`() = runTest {
        val userPreferencesRepository = FakeUserPreferencesRepository()

        repository(FakeLibraryFileLocalSource(), userPreferencesRepository)
            .rememberDemoLibraryFiles(songFileNames = listOf("missing.cho"), setlistFileNames = emptyList())

        assertEquals(emptyMap(), userPreferencesRepository.current.demoLibraryContentHashes)
    }

    @Test
    fun `a failing read records nothing and does not throw`() = runTest {
        val libraryFileLocalSource = FakeLibraryFileLocalSource(
            files = mapOf(SyncKey(kind = LibraryFileKind.SONG, name = "a.cho") to "Song a".encodeToByteArray()),
            onRead = { throw LibraryStorageException("Unreadable") },
        )
        val userPreferencesRepository = FakeUserPreferencesRepository()

        repository(libraryFileLocalSource, userPreferencesRepository)
            .rememberDemoLibraryFiles(songFileNames = listOf("a.cho"), setlistFileNames = emptyList())

        assertEquals(emptyMap(), userPreferencesRepository.current.demoLibraryContentHashes)
    }

    private fun repository(
        libraryFileLocalSource: FakeLibraryFileLocalSource,
        userPreferencesRepository: FakeUserPreferencesRepository,
    ) = DemoLibraryRepositoryImpl(
        libraryFileLocalSource = libraryFileLocalSource,
        libraryFileLock = LibraryFileLock(),
        userPreferencesRepository = userPreferencesRepository,
    )
}
