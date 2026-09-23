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

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.source.local.api.SyncStateLocalSource
import com.pandulapeter.campfire.data.source.remote.api.PendingAuthorization
import com.pandulapeter.campfire.data.source.remote.api.PendingAuthorizationStore
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthenticator
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteAuthorizationRequest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The two documents sync keeps between runs, held in memory. [onSaveIndex] runs before a write of the index is
 * stored, and [onLoadIndex] before a read of it answers, which is where a test makes either fail by throwing from it. A cancelled caller's write is refused the
 * way the real storage's is, whose writes are a `withContext` and so answer a cancellation on the way in.
 */
internal class FakeSyncStateLocalSource(
    var index: String? = null,
    var onSaveIndex: (String?) -> Unit = {},
    var onLoadIndex: () -> Unit = {},
) : SyncStateLocalSource {

    var credentials: String? = null

    override suspend fun loadSyncCredentials() = credentials

    override suspend fun saveSyncCredentials(document: String?) {
        credentials = document
    }

    override suspend fun loadSyncIndex(): String? {
        onLoadIndex()
        return index
    }

    override suspend fun saveSyncIndex(document: String?) {
        currentCoroutineContext().ensureActive()
        onSaveIndex(document)
        index = document
    }
}

/**
 * A platform whose consent page answers with [outcome] every time, and that has no redirect waiting at start up.
 * Without an outcome, being asked for consent at all is a mistake of the test. [onAuthorize] runs before the page
 * answers, which is where a test keeps the user on it.
 */
internal class FakeSyncAuthenticator(
    private val outcome: SyncAuthenticator.AuthorizationOutcome? = null,
    private val onAuthorize: suspend () -> Unit = {},
) : SyncAuthenticator {

    override suspend fun prepareRedirectUri(): String? = null

    override suspend fun authorize(
        authorizationUrl: String,
        completionPage: AuthorizationCompletionPage,
    ): SyncAuthenticator.AuthorizationOutcome {
        onAuthorize()
        return outcome ?: throw UnsupportedOperationException()
    }

    override suspend fun consumePendingRedirect(): String? = null
}

/**
 * The authorization that was started and not finished, held in memory. [onWrite] runs before every save and clear,
 * which is where a test makes the storage refuse them.
 */
internal class FakePendingAuthorizationStore : PendingAuthorizationStore {

    var pending: PendingAuthorization? = null
    var onWrite: () -> Unit = {}

    override suspend fun savePendingAuthorization(providerId: SyncProviderId, request: RemoteAuthorizationRequest) {
        onWrite()
        pending = PendingAuthorization(
            providerId = providerId,
            state = request.state,
            verifier = request.verifier,
            redirectUri = request.redirectUri,
        )
    }

    override suspend fun loadPendingAuthorization() = pending

    override suspend fun clearPendingAuthorization() {
        onWrite()
        pending = null
    }
}

/**
 * Stands in for the song list that sync tells to read the library again, and counts how often it was told. [onRescan]
 * runs on every rescan, which is where a test sees what the library held at that moment.
 */
internal class RecordingSongRepository(
    private val onRescan: () -> Unit = {},
) : SongRepository {

    var rescanCount = 0

    override val songs: Flow<DataState<List<Song>>> = emptyFlow()

    override suspend fun loadSongsIfNeeded(): List<Song>? = throw UnsupportedOperationException()

    override suspend fun loadSongFileSizes(): Map<String, Long> = throw UnsupportedOperationException()

    override suspend fun rescan() {
        rescanCount++
        onRescan()
    }

    override suspend fun saveSong(content: SongContent, expectedText: String?): Boolean = throw UnsupportedOperationException()

    override suspend fun createSong(title: String, artist: String, text: String): Song = throw UnsupportedOperationException()

    override fun importFileName(fallbackTitle: String, text: String): String = throw UnsupportedOperationException()

    override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean): Song =
        throw UnsupportedOperationException()

    override suspend fun renameSong(song: Song): Song? = throw UnsupportedOperationException()

    override suspend fun deleteSong(fileName: String): Unit = throw UnsupportedOperationException()
}

/** Stands in for the setlist list that sync tells to read the library again, and counts how often it was told. */
internal class RecordingSetlistRepository : SetlistRepository {

    var rescanCount = 0

    override val setlists: Flow<DataState<List<Setlist>>> = emptyFlow()

    override suspend fun loadSetlistsIfNeeded(): List<Setlist>? = throw UnsupportedOperationException()

    override suspend fun loadSetlistFileNamesNaming(songFileName: String): List<String> = throw UnsupportedOperationException()

    override suspend fun rescan() {
        rescanCount++
    }

    override suspend fun createSetlist(title: String, description: String, priority: Int): Setlist =
        throw UnsupportedOperationException()

    override suspend fun saveSetlist(setlist: Setlist): Unit = throw UnsupportedOperationException()

    override suspend fun updateSetlist(fileName: String, transform: (Setlist) -> Setlist): Setlist? =
        throw UnsupportedOperationException()

    override suspend fun renameSetlist(fileName: String, title: String, description: String): Setlist? =
        throw UnsupportedOperationException()

    override suspend fun parseSetlist(document: String): Setlist? = throw UnsupportedOperationException()

    override suspend fun importSetlist(setlist: Setlist, shouldReplace: Boolean): Setlist = throw UnsupportedOperationException()

    override suspend fun loadSetlistDocument(fileName: String): String? = throw UnsupportedOperationException()

    override suspend fun deleteSetlist(fileName: String): Unit = throw UnsupportedOperationException()
}
