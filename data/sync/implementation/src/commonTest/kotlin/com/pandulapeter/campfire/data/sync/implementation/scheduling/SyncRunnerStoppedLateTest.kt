/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.scheduling

import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.sync.SyncOutcome
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.sync.implementation.FakeLibraryFileLocalSource
import com.pandulapeter.campfire.data.sync.implementation.FakeSyncIndexLocalSource
import com.pandulapeter.campfire.data.sync.implementation.FakeSyncProvider
import com.pandulapeter.campfire.data.sync.implementation.FakeUserPreferencesRepository
import com.pandulapeter.campfire.data.sync.implementation.RecordingSongRepository
import com.pandulapeter.campfire.data.sync.implementation.SyncIndexEntry
import com.pandulapeter.campfire.data.sync.implementation.TEST_ACCOUNT
import com.pandulapeter.campfire.data.sync.implementation.TEST_NOW
import com.pandulapeter.campfire.data.sync.implementation.awaitOutcome
import com.pandulapeter.campfire.data.sync.implementation.defaultUserPreferences
import com.pandulapeter.campfire.data.sync.implementation.encoded
import com.pandulapeter.campfire.data.sync.implementation.index.SyncIndexDocument
import com.pandulapeter.campfire.data.sync.implementation.index.indexKey
import com.pandulapeter.campfire.data.sync.implementation.preferences.SyncedPreferencesDocument
import com.pandulapeter.campfire.data.sync.implementation.song
import com.pandulapeter.campfire.data.sync.implementation.syncRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * A run stopped after its files are settled, while `preferences.json` is or once the index has been saved: what it leaves
 * in the index is never older than what the run had already written, so the next run does not undo it.
 */
class SyncRunnerStoppedLateTest {

    @Test
    fun `a run stopped while the library reads what it changed keeps the completed index`() = runTest {
        val folderDocument = document("""{"song_1.cho":{"capo":3}}""")
        val provider = FakeSyncProvider(files = mapOf(song(1) to ONE), account = TEST_ACCOUNT)
        provider.documents[SyncedPreferencesDocument.FILE_NAME] = SyncedPreferencesDocument.encode(folderDocument) to "r1"
        val stateLocalSource = FakeSyncIndexLocalSource()
        val refreshStarted = CompletableDeferred<Unit>()
        val songRepository = RecordingSongRepository(
            onRefresh = {
                if (!refreshStarted.isCompleted) {
                    refreshStarted.complete(Unit)
                    awaitCancellation()
                }
            },
        )
        val repository = syncRepository(
            provider = provider,
            stateLocalSource = stateLocalSource,
            songRepository = songRepository,
            userPreferencesRepository = FakeUserPreferencesRepository(),
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        refreshStarted.await()
        repository.cancelSynchronization()

        assertEquals(SyncOutcome.Interrupted, repository.awaitOutcome().lastOutcome)
        val saved = assertNotNull(stateLocalSource.document)
        assertEquals(folderDocument, saved.syncedPreferences)
        assertEquals(TEST_NOW.toEpochMilliseconds(), saved.lastSyncedAt)
        assertFalse(saved.isRunInProgress)
    }

    @Test
    fun `a run stopped while preferences go up keeps the folder's document as the base`() = runTest {
        val base = document("""{"song_1.cho":{"capo":1}}""")
        val folderDocument = document("""{"song_1.cho":{"capo":2}}""")
        val provider = FakeSyncProvider(files = mapOf(song(1) to ONE), account = TEST_ACCOUNT)
        provider.documents[SyncedPreferencesDocument.FILE_NAME] = SyncedPreferencesDocument.encode(folderDocument) to "r1"
        val stateLocalSource = FakeSyncIndexLocalSource(
            index = SyncIndexDocument.of(
                providerId = SyncProviderId.DROPBOX.id,
                accountId = TEST_ACCOUNT.indexKey(),
                lastSyncedAt = 1,
                syncedPreferences = base,
                index = mapOf(song(1) to SyncIndexEntry(localHash = localContentHash(ONE), remoteRevision = "r1")),
            ).encoded(),
        )
        // The transposition is set only here since the last run, so the folder does not have it yet.
        val preferences = FakeUserPreferencesRepository(
            defaultUserPreferences(capos = mapOf("song_1.cho" to 1), transpositions = mapOf("song_1.cho" to 2)),
        )
        val uploadStarted = CompletableDeferred<Unit>()
        provider.onUploadDocument = {
            uploadStarted.complete(Unit)
            awaitCancellation()
        }
        val repository = syncRepository(
            provider = provider,
            stateLocalSource = stateLocalSource,
            libraryFileLocalSource = FakeLibraryFileLocalSource(files = mapOf(song(1) to ONE)),
            userPreferencesRepository = preferences,
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        uploadStarted.await()
        repository.cancelSynchronization()

        assertEquals(SyncOutcome.Interrupted, repository.awaitOutcome().lastOutcome)
        val saved = assertNotNull(stateLocalSource.document)
        assertEquals(folderDocument, saved.syncedPreferences)
        assertEquals(1, saved.lastSyncedAt)

        provider.onUploadDocument = {}
        repository.synchronize(SyncDeletionPolicy.ASK)
        repository.syncState.first { (it as? SyncState.Connected)?.lastOutcome is SyncOutcome.Success }

        val uploaded = SyncedPreferencesDocument.preferencesOf(
            assertNotNull(SyncedPreferencesDocument.decode(provider.documents.getValue(SyncedPreferencesDocument.FILE_NAME).first)),
        )
        assertEquals(mapOf("song_1.cho" to 2), uploaded.transpositions)
        assertEquals(mapOf("song_1.cho" to 2), uploaded.capos)
        assertEquals(mapOf("song_1.cho" to 2), preferences.current.transpositions)
    }

    /** A document holding [songs] and nothing else, as this version writes one. */
    private fun document(songs: String) = JsonObject(mapOf("version" to JsonPrimitive(1), "songs" to Json.parseToJsonElement(songs)))

    private companion object {
        val ONE = "One".encodeToByteArray()
    }
}
