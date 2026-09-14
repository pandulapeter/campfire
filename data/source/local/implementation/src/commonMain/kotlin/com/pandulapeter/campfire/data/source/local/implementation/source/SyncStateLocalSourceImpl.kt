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

import com.pandulapeter.campfire.data.source.local.api.SyncStateLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import com.pandulapeter.campfire.data.source.local.implementation.storage.secret.SecretStore
import kotlinx.coroutines.CancellationException
import org.koin.core.annotation.Single

@Single
internal class SyncStateLocalSourceImpl(
    private val fileStorage: FileStorage,
    private val secretStore: SecretStore,
) : SyncStateLocalSource {

    /** Unreadable credentials are treated like the index below: as none, which the user answers by connecting again. */
    override suspend fun loadSyncCredentials() = try {
        secretStore.load(CREDENTIALS_FILE_NAME) ?: migratePlainFileIfPresent()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        // The message of a failure is free to quote what it failed on, which here would be a token.
        println("Could not read the sync credentials: ${exception::class.simpleName}")
        null
    }

    override suspend fun saveSyncCredentials(document: String?) = secretStore.save(CREDENTIALS_FILE_NAME, document)

    override suspend fun loadSyncIndex() = read(INDEX_FILE_NAME)

    override suspend fun saveSyncIndex(document: String?) = write(INDEX_FILE_NAME, document)

    /**
     * Credentials written as a plain file, before they moved into the platform's secret store, are moved there on the
     * first read, so an update does not disconnect anybody. Where the secret store is that same plain file (desktop
     * and the web) it has already found it, and this only runs when there is no file at all.
     */
    private suspend fun migratePlainFileIfPresent(): String? {
        val document = read(CREDENTIALS_FILE_NAME) ?: return null
        secretStore.save(CREDENTIALS_FILE_NAME, document)
        fileStorage.delete(StorageDirectory.PREFERENCES, CREDENTIALS_FILE_NAME)
        println("Moved the sync credentials into the platform's secret store.")
        return document
    }

    /** A document that cannot be read is treated as one that is not there: sync then starts from nothing. */
    private suspend fun read(name: String) = try {
        fileStorage.readText(StorageDirectory.PREFERENCES, name)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        // The credentials hold tokens, and a message is free to quote what it failed on, so for them only the kind of
        // failure goes into the log.
        val reason = if (name == CREDENTIALS_FILE_NAME) exception::class.simpleName else exception.message
        println("Could not read \"$name\": $reason")
        null
    }

    private suspend fun write(name: String, document: String?) = if (document == null) {
        fileStorage.delete(StorageDirectory.PREFERENCES, name)
    } else {
        fileStorage.writeText(StorageDirectory.PREFERENCES, name, document)
    }

    private companion object {
        /** The name of the plain file on desktop and the web, and the key of the secret store's entry everywhere. */
        const val CREDENTIALS_FILE_NAME = "sync-credentials.json"
        const val INDEX_FILE_NAME = "sync-index.json"
    }
}
