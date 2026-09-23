/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.secret.SecretStore
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Credentials that cannot be read right now are not the same as none, and only the first may be written over. */
class SyncStateLocalSourceTest {

    private val root: File = Files.createTempDirectory("campfire-sync-state").toFile()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `credentials the secret store refuses for now are reported as a storage failure`() = runBlocking<Unit> {
        val localSource = SyncStateLocalSourceImpl(JvmFileStorage(root), FailingSecretStore(LibraryStorageException("Keystore busy")))

        assertFailsWith<LibraryStorageException> { localSource.loadSyncCredentials() }
    }

    @Test
    fun `credentials that fail to read in any other way are read as none`() = runBlocking {
        val localSource = SyncStateLocalSourceImpl(JvmFileStorage(root), FailingSecretStore(IllegalStateException("Broken")))

        assertNull(localSource.loadSyncCredentials())
    }

    /** A secret store whose every read ends in [exception]. */
    private class FailingSecretStore(private val exception: Exception) : SecretStore {

        override suspend fun load(key: String): String? = throw exception

        override suspend fun save(key: String, value: String?) = Unit
    }
}
