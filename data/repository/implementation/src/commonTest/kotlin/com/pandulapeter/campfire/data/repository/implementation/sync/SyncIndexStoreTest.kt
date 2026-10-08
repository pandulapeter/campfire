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

import com.pandulapeter.campfire.data.repository.implementation.base.testEnvironment
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SyncIndexStoreTest {

    @Test
    fun `a document that does not decode loads as an empty index`() = runTest {
        val store = SyncIndexStore(FakeSyncIndexLocalSource(index = "not json"), testEnvironment())

        assertEquals(SyncIndexDocument(), store.load())
    }

    @Test
    fun `a storage that cannot be read makes load throw and loadOrNull answer null`() = runTest {
        val store = SyncIndexStore(FakeSyncIndexLocalSource(onLoadIndex = { throw LibraryStorageException("Locked") }), testEnvironment())

        assertFailsWith<LibraryStorageException> { store.load() }
        assertNull(store.loadOrNull())
    }

    @Test
    fun `a quiet save swallows a storage failure`() = runTest {
        val localSource = FakeSyncIndexLocalSource(index = "{}", onSaveIndex = { throw LibraryStorageException("Full") })
        val store = SyncIndexStore(localSource, testEnvironment())

        store.saveQuietly(SyncIndexDocument())

        assertEquals("{}", localSource.index)
    }
}
