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

import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import com.pandulapeter.campfire.data.source.local.implementation.storage.secret.SecretStore
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Credentials that cannot be read right now are not the same as none, and only the first may be written over. */
class SyncCredentialsLocalSourceTest {

    private val root: File = Files.createTempDirectory("campfire-sync-credentials").toFile()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `credentials the secret store refuses for now are reported as a storage failure`() = runBlocking<Unit> {
        val localSource = SyncCredentialsLocalSourceImpl(JvmFileStorage(root), FailingSecretStore(LibraryStorageException("Keystore busy")), Logger.Standard)

        assertFailsWith<LibraryStorageException> { localSource.loadSyncCredentials() }
    }

    @Test
    fun `credentials that fail to read in any other way are read as none`() = runBlocking {
        val localSource = SyncCredentialsLocalSourceImpl(JvmFileStorage(root), FailingSecretStore(IllegalStateException("Broken")), Logger.Standard)

        assertNull(localSource.loadSyncCredentials())
    }

    @Test
    fun `credentials an older version left in a plain file move into the secret store`() = runBlocking {
        val fileStorage = JvmFileStorage(root)
        fileStorage.writeText(StorageDirectory.PREFERENCES, CREDENTIALS, DOCUMENT)
        val secretStore = InMemorySecretStore()
        val logger = RecordingLogger()

        val loaded = SyncCredentialsLocalSourceImpl(fileStorage, secretStore, logger).loadSyncCredentials()

        assertEquals(DOCUMENT, loaded)
        assertEquals(DOCUMENT, secretStore.load(CREDENTIALS))
        assertFalse(fileStorage.exists(StorageDirectory.PREFERENCES, CREDENTIALS))
        assertFalse(logger.lines.any { TOKEN in it }, logger.lines.toString())
    }

    @Test
    fun `credentials already in the secret store win over a plain file`() = runBlocking {
        val fileStorage = JvmFileStorage(root)
        fileStorage.writeText(StorageDirectory.PREFERENCES, CREDENTIALS, """{"refreshToken":"stale"}""")
        val secretStore = InMemorySecretStore().apply { save(CREDENTIALS, DOCUMENT) }

        assertEquals(DOCUMENT, SyncCredentialsLocalSourceImpl(fileStorage, secretStore, Logger.Standard).loadSyncCredentials())
        assertEquals(DOCUMENT, secretStore.load(CREDENTIALS))
    }

    @Test
    fun `a failure that quotes the token is logged without it`() = runBlocking {
        val logger = RecordingLogger()
        val localSource = SyncCredentialsLocalSourceImpl(JvmFileStorage(root), FailingSecretStore(IllegalStateException("Broken: $DOCUMENT")), logger)

        assertNull(localSource.loadSyncCredentials())
        assertFalse(logger.lines.isEmpty())
        assertFalse(logger.lines.any { TOKEN in it }, logger.lines.toString())
    }

    /** A secret store whose every read ends in [exception]. */
    private class FailingSecretStore(private val exception: Exception) : SecretStore {

        override suspend fun load(key: String): String? = throw exception

        override suspend fun save(key: String, value: String?) = Unit
    }

    /** A secret store that keeps its values in memory, as an empty Keystore or Keychain would start. */
    private class InMemorySecretStore : SecretStore {

        private val values = mutableMapOf<String, String>()

        override suspend fun load(key: String) = values[key]

        override suspend fun save(key: String, value: String?) {
            if (value == null) values -= key else values[key] = value
        }
    }

    /** Keeps every line, so that a test can check that none of them quotes a token. */
    private class RecordingLogger : Logger {

        val lines = mutableListOf<String>()

        override fun log(message: String) {
            lines += message
        }
    }

    private companion object {
        const val CREDENTIALS = "sync-credentials.json"
        const val TOKEN = "secret-refresh-token"
        const val DOCUMENT = """{"refreshToken":"$TOKEN"}"""
    }
}
