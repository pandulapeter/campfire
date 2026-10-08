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

import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The note that forgetting a previous installation's credentials is still owed, as a file of this installation's own. */
class SyncIndexLocalSourceTest {

    private val root: File = Files.createTempDirectory("campfire-sync-index").toFile()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `the forgetting note is written and crossed off`() = runBlocking {
        val fileStorage = JvmFileStorage(root)
        val localSource = SyncIndexLocalSourceImpl(fileStorage)
        assertFalse(localSource.isForgettingCredentialsOwed())

        localSource.setForgettingCredentialsOwed(true)
        assertTrue(localSource.isForgettingCredentialsOwed())
        assertTrue(fileStorage.exists(StorageDirectory.PREFERENCES, FORGETTING_OWED_FILE_NAME))

        localSource.setForgettingCredentialsOwed(false)
        assertFalse(localSource.isForgettingCredentialsOwed())
        assertFalse(fileStorage.exists(StorageDirectory.PREFERENCES, FORGETTING_OWED_FILE_NAME))

        localSource.setForgettingCredentialsOwed(false)
        assertFalse(localSource.isForgettingCredentialsOwed())
    }

    private companion object {
        const val FORGETTING_OWED_FILE_NAME = "sync-credentials-forget-pending"
    }
}
