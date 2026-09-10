package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.source.local.api.SyncStateLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory

internal class SyncStateLocalSourceImpl(
    private val fileStorage: FileStorage
) : SyncStateLocalSource {

    override suspend fun loadSyncCredentials() = read(CREDENTIALS_FILE_NAME)

    override suspend fun saveSyncCredentials(document: String?) = write(CREDENTIALS_FILE_NAME, document)

    override suspend fun loadSyncIndex() = read(INDEX_FILE_NAME)

    override suspend fun saveSyncIndex(document: String?) = write(INDEX_FILE_NAME, document)

    /** A document that cannot be read is treated as one that is not there: sync then starts from nothing. */
    private suspend fun read(name: String) = try {
        fileStorage.readText(StorageDirectory.PREFERENCES, name)
    } catch (exception: Exception) {
        println("Could not read \"$name\": ${exception.message}")
        null
    }

    private suspend fun write(name: String, document: String?) = if (document == null) {
        fileStorage.delete(StorageDirectory.PREFERENCES, name)
    } else {
        fileStorage.writeText(StorageDirectory.PREFERENCES, name, document)
    }

    private companion object {
        const val CREDENTIALS_FILE_NAME = "sync-credentials.json"
        const val INDEX_FILE_NAME = "sync-index.json"
    }
}
