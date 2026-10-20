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
import com.pandulapeter.campfire.data.sync.implementation.FakeLibraryFileLocalSource
import com.pandulapeter.campfire.data.sync.implementation.FakeSyncIndexLocalSource
import com.pandulapeter.campfire.data.sync.implementation.FakeSyncProvider
import com.pandulapeter.campfire.data.sync.implementation.FakeUserPreferencesRepository
import com.pandulapeter.campfire.data.sync.implementation.TEST_ACCOUNT
import com.pandulapeter.campfire.data.sync.implementation.awaitOutcome
import com.pandulapeter.campfire.data.sync.implementation.defaultUserPreferences
import com.pandulapeter.campfire.data.sync.implementation.preferences.SyncedPreferencesDocument
import com.pandulapeter.campfire.data.sync.implementation.song
import com.pandulapeter.campfire.data.sync.implementation.syncRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * `preferences.json` is downloaded only when the run's listing shows a revision other than the one the index pairs with
 * the document the last settle left, through the real [SyncRunner], which is what files that revision in the index.
 */
class SyncRunnerPreferencesRevisionTest {

    @Test
    fun `a run downloads the preferences only when the folder's revision moved`() = runTest {
        val provider = FakeSyncProvider(files = mapOf(song(1) to ONE), account = TEST_ACCOUNT)
        provider.documents[SyncedPreferencesDocument.FILE_NAME] = SyncedPreferencesDocument.encode(document("""{"song_1.cho":{"capo":3}}""")) to "r1"
        val stateLocalSource = FakeSyncIndexLocalSource()
        val preferences = FakeUserPreferencesRepository(defaultUserPreferences(capos = mapOf("song_1.cho" to 3)))
        val repository = syncRepository(
            provider = provider,
            stateLocalSource = stateLocalSource,
            libraryFileLocalSource = FakeLibraryFileLocalSource(files = mapOf(song(1) to ONE)),
            userPreferencesRepository = preferences,
        )
        repository.restore {}

        repository.synchronize(SyncDeletionPolicy.ASK)
        assertIs<SyncOutcome.Success>(repository.awaitOutcome().lastOutcome)
        assertEquals(
            provider.documents.getValue(SyncedPreferencesDocument.FILE_NAME).second,
            assertNotNull(stateLocalSource.document).syncedPreferencesRevision,
        )
        val downloads = provider.documentDownloadCount

        repository.synchronize(SyncDeletionPolicy.ASK)
        assertIs<SyncOutcome.Success>(repository.awaitOutcome().lastOutcome)

        assertEquals(downloads, provider.documentDownloadCount)

        provider.writeDocumentElsewhere(
            name = SyncedPreferencesDocument.FILE_NAME,
            bytes = SyncedPreferencesDocument.encode(document("""{"song_1.cho":{"capo":5}}""")),
        )
        repository.synchronize(SyncDeletionPolicy.ASK)
        assertIs<SyncOutcome.Success>(repository.awaitOutcome().lastOutcome)

        assertEquals(downloads + 1, provider.documentDownloadCount)
        assertEquals(mapOf("song_1.cho" to 5), preferences.current.capos)
    }

    /** A document holding [songs] and nothing else, as this version writes one. */
    private fun document(songs: String) = JsonObject(mapOf("version" to JsonPrimitive(1), "songs" to Json.parseToJsonElement(songs)))

    private companion object {
        val ONE = "One".encodeToByteArray()
    }
}
