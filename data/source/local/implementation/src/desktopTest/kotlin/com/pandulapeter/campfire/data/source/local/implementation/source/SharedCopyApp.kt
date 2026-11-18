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

import com.pandulapeter.campfire.data.model.importing.ImportDownload
import com.pandulapeter.campfire.data.model.share.CreateShareResult
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.sync.SyncOutcome
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.data.repository.api.ImportDownloadRepository
import com.pandulapeter.campfire.data.repository.api.SyncRepository
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncProviders
import com.pandulapeter.campfire.domain.api.sharing.CreateShareLinkUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files

/**
 * One device of [library], wired as the app wires it ([importGraph]) but with sync connected to an
 * [InMemorySyncProvider] in place of Dropbox, and its writes not flushed ([UnflushedFileStorage]): the library is written
 * into its folder before anything reads it, then its items are shared through [CreateShareLinkUseCase] and the library
 * synced, as a user does. With [isExactNames] the file system finds a library file only under its stored spelling, as
 * Android's and Linux's do.
 */
internal class SharedCopyApp(private val library: SharedCopyLibrary, isExactNames: Boolean) : AutoCloseable {

    private val root: File = Files.createTempDirectory("campfire-shared-copy").toFile()
    private val storage: FileStorage = UnflushedFileStorage(root).let { if (isExactNames) ExactNameFileStorage(it) else it }
    val provider = InMemorySyncProvider()
    private val koinApplication = importGraph(
        storage = storage,
        downloads = NoDownloads,
    ) {
        single { SyncProviders(all = listOf(provider), loadStoredProviderId = { provider.id }) }
    }
    private val koin = koinApplication.koin
    private val syncRepository get() = koin.get<SyncRepository>()

    suspend fun start() {
        (library.songs + library.unsharedSong).forEach { storage.writeBytes(StorageDirectory.SONGS, it.name, it.bytes) }
        library.setlists.forEach { storage.writeBytes(StorageDirectory.SETLISTS, it.name, it.bytes) }
        library.files.forEach { (name, bytes) -> storage.writeBytes(StorageDirectory.FILES, name, bytes) }
        storage.writeBytes(StorageDirectory.PREFERENCES, PREFERENCES, library.preferences)
        check(syncRepository.restore {}.isConnected) { "Seed ${library.seed}: sync did not connect." }
    }

    suspend fun share(kind: SharedLinkKind, fileName: String): CreateShareResult.Shared {
        val result = koin.get<CreateShareLinkUseCase>()(kind = kind, fileName = fileName, title = fileName)
        return result as? CreateShareResult.Shared ?: error("Seed ${library.seed}: $fileName was not shared: $result")
    }

    /**
     * Starts a run as the Sync button does and waits for its outcome. A run the app started on its own (one a change it
     * made asks for) may be going, which this one follows; whatever that one uploads counts as much.
     */
    suspend fun synchronize(): SyncOutcome = withTimeout(RUN_TIMEOUT) {
        // A run going clears the last outcome as it starts, so the one awaited below is this run's.
        while (!syncRepository.synchronize()) delay(RETRY_DELAY)
        connectedState { !it.isSyncing && it.lastOutcome != null }.lastOutcome!!
    }

    suspend fun editUnsharedSong() = storage.writeBytes(StorageDirectory.SONGS, library.unsharedSong.name, library.unsharedSong.edited)

    private suspend fun connectedState(predicate: (SyncState.Connected) -> Boolean) =
        syncRepository.syncState.first { it is SyncState.Connected && predicate(it) } as SyncState.Connected

    override fun close() {
        koinApplication.close()
        provider.clear()
        root.deleteRecursively()
    }

    private object NoDownloads : ImportDownloadRepository {

        override suspend fun download(url: String, onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit): ImportDownload =
            throw NotImplementedError("Sharing and sync never download through the import.")

        override suspend fun downloadPicture(url: String, maxSizeBytes: Long): ImportDownload =
            throw NotImplementedError("Sharing and sync never download through the import.")
    }

    private companion object {
        const val PREFERENCES = "preferences.json"
        const val RUN_TIMEOUT = 60_000L
        const val RETRY_DELAY = 5L
    }
}
