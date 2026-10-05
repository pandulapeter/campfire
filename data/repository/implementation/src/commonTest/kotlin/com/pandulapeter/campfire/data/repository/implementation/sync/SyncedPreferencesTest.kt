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

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteWriteResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncedPreferencesTest {

    // The merge

    @Test
    fun `two devices that changed different songs both keep their change`() = assertEquals(
        document("""{"a.cho":{"transposition":3},"b.cho":{"capo":2}}"""),
        merge(
            base = """{"a.cho":{"transposition":1}}""",
            local = """{"a.cho":{"transposition":3}}""",
            remote = """{"a.cho":{"transposition":1},"b.cho":{"capo":2}}""",
        ),
    )

    @Test
    fun `two devices that changed different fields of one song both keep their change`() = assertEquals(
        document("""{"a.cho":{"transposition":3,"tempo":90}}"""),
        merge(
            base = """{"a.cho":{"transposition":1,"tempo":120}}""",
            local = """{"a.cho":{"transposition":3,"tempo":120}}""",
            remote = """{"a.cho":{"transposition":1,"tempo":90}}""",
        ),
    )

    @Test
    fun `a value removed on one side is removed`() = assertEquals(
        document("""{}"""),
        merge(
            base = """{"a.cho":{"capo":2}}""",
            local = """{"a.cho":{"capo":2}}""",
            remote = """{}""",
        ),
    )

    @Test
    fun `a change beats a removal`() = assertEquals(
        document("""{"a.cho":{"capo":4}}"""),
        merge(
            base = """{"a.cho":{"capo":2}}""",
            local = """{}""",
            remote = """{"a.cho":{"capo":4}}""",
        ),
    )

    @Test
    fun `two different changes of one value keep this device's`() = assertEquals(
        document("""{"a.cho":{"capo":4}}"""),
        merge(
            base = """{"a.cho":{"capo":2}}""",
            local = """{"a.cho":{"capo":4}}""",
            remote = """{"a.cho":{"capo":5}}""",
        ),
    )

    @Test
    fun `without a base nothing is removed`() = assertEquals(
        document("""{"a.cho":{"capo":4},"b.cho":{"tempo":100}}"""),
        merge(
            base = null,
            local = """{"a.cho":{"capo":4}}""",
            remote = """{"b.cho":{"tempo":100}}""",
        ),
    )

    @Test
    fun `a song both sides took a different field off is dropped`() = assertEquals(
        document("""{}"""),
        merge(
            base = """{"a.cho":{"capo":2,"tempo":100}}""",
            local = """{"a.cho":{"tempo":100}}""",
            remote = """{"a.cho":{"capo":2}}""",
        ),
    )

    // The document

    @Test
    fun `what this version does not know is carried over from the base`() {
        val base = Json.parseToJsonElement(
            """{"version":2,"settings":{"theme":"dark"},"songs":{"a.cho":{"capo":2,"strumming":"DDU"}}}""",
        ).jsonObject
        assertEquals(
            Json.parseToJsonElement(
                """{"version":2,"settings":{"theme":"dark"},"songs":{"a.cho":{"strumming":"DDU"},"b.cho":{"tempo":90}}}""",
            ),
            SyncedPreferencesDocument.localDocument(base = base, preferences = SyncedPreferences(tempos = mapOf("b.cho" to 90))),
        )
    }

    @Test
    fun `values that could not have been written are not read`() = assertEquals(
        SyncedPreferences(transpositions = mapOf("a.cho" to 2), tempos = mapOf("a.cho" to 120)),
        SyncedPreferencesDocument.preferencesOf(
            Json.parseToJsonElement(
                """{"songs":{"a.cho":{"transposition":2,"tempo":120,"capo":40},"b.cho":{"tempo":"fast","transposition":0},"c.cho":3}}""",
            ).jsonObject,
        ),
    )

    @Test
    fun `the same preferences are always the same bytes`() = assertEquals(
        SyncedPreferencesDocument.encode(Json.parseToJsonElement("""{"songs":{"b.cho":{"tempo":1},"a.cho":{"capo":1}},"version":1}""").jsonObject)
            .decodeToString(),
        SyncedPreferencesDocument.encode(Json.parseToJsonElement("""{"version":1,"songs":{"a.cho":{"capo":1},"b.cho":{"tempo":1}}}""").jsonObject)
            .decodeToString(),
    )

    @Test
    fun `a document that is not one is read as none`() = assertNull(SyncedPreferencesDocument.decode("[1, 2".encodeToByteArray()))

    @Test
    fun `a value changed here during the merge is kept`() {
        val preferences = defaultUserPreferences(transpositions = mapOf("a.cho" to 5, "b.cho" to 1))
        val applied = SyncedPreferences(transpositions = mapOf("a.cho" to 2, "b.cho" to 3)).applyTo(
            preferences = preferences,
            since = SyncedPreferences(transpositions = mapOf("a.cho" to 1, "b.cho" to 1)),
        )
        assertEquals(mapOf("a.cho" to 5, "b.cho" to 3), applied.transpositions)
    }

    // The run's step

    @Test
    fun `a first run uploads what is set here`() = runTest {
        val provider = FakeSyncProvider()
        val preferences = FakeUserPreferencesRepository(defaultUserPreferences(capos = mapOf("a.cho" to 2)))
        val synced = sync(preferences, library("a.cho")).synchronize(provider, base = null, keptFileNames = emptyList())
        assertEquals(remoteDocumentOf(provider), synced)
        assertEquals(mapOf("a.cho" to 2), SyncedPreferencesDocument.preferencesOf(synced!!).capos)
    }

    @Test
    fun `what another device set arrives here`() = runTest {
        val provider = FakeSyncProvider()
        provider.documents[SyncedPreferencesDocument.FILE_NAME] = encoded("""{"a.cho":{"tempo":140}}""") to "r1"
        val preferences = FakeUserPreferencesRepository()
        sync(preferences, library("a.cho")).synchronize(provider, base = null, keptFileNames = emptyList())
        assertEquals(mapOf("a.cho" to 140), preferences.current.tempos)
        assertEquals("r1", provider.documents.getValue(SyncedPreferencesDocument.FILE_NAME).second)
    }

    @Test
    fun `a song that is not in the library takes its preferences with it on both sides`() = runTest {
        val provider = FakeSyncProvider()
        provider.documents[SyncedPreferencesDocument.FILE_NAME] = encoded("""{"gone.cho":{"tempo":140},"a.cho":{"capo":1}}""") to "r1"
        val preferences = FakeUserPreferencesRepository(defaultUserPreferences(transpositions = mapOf("deleted.cho" to 2)))
        sync(preferences, library("a.cho")).synchronize(provider, base = null, keptFileNames = emptyList())
        assertEquals(SyncedPreferences(capos = mapOf("a.cho" to 1)), SyncedPreferences.of(preferences.current))
        assertEquals(SyncedPreferences(capos = mapOf("a.cho" to 1)), SyncedPreferencesDocument.preferencesOf(remoteDocumentOf(provider)))
    }

    @Test
    fun `a song the run could not move keeps its preferences`() = runTest {
        val provider = FakeSyncProvider()
        provider.documents[SyncedPreferencesDocument.FILE_NAME] = encoded("""{"Big.cho":{"tempo":140}}""") to "r1"
        val preferences = FakeUserPreferencesRepository()
        sync(preferences, library()).synchronize(provider, base = null, keptFileNames = listOf("big.cho"))
        assertEquals(mapOf("Big.cho" to 140), preferences.current.tempos)
    }

    @Test
    fun `a document written elsewhere during the upload is merged again`() = runTest {
        val provider = FakeSyncProvider()
        provider.documents[SyncedPreferencesDocument.FILE_NAME] = encoded("""{}""") to "r1"
        var hasWrittenElsewhere = false
        provider.onUploadDocument = {
            if (!hasWrittenElsewhere) {
                hasWrittenElsewhere = true
                provider.documents[SyncedPreferencesDocument.FILE_NAME] = encoded("""{"b.cho":{"capo":3}}""") to "r9"
            }
        }
        val preferences = FakeUserPreferencesRepository(defaultUserPreferences(capos = mapOf("a.cho" to 2)))
        val synced = sync(preferences, library("a.cho", "b.cho")).synchronize(provider, base = document("{}"), keptFileNames = emptyList())
        val expected = SyncedPreferences(capos = mapOf("a.cho" to 2, "b.cho" to 3))
        assertEquals(expected, SyncedPreferences.of(preferences.current))
        assertEquals(expected, SyncedPreferencesDocument.preferencesOf(remoteDocumentOf(provider)))
        assertEquals(remoteDocumentOf(provider), synced)
    }

    @Test
    fun `a document already in step is not uploaded again`() = runTest {
        val provider = FakeSyncProvider()
        val preferences = FakeUserPreferencesRepository(defaultUserPreferences(capos = mapOf("a.cho" to 2)))
        val step = sync(preferences, library("a.cho"))
        val synced = step.synchronize(provider, base = null, keptFileNames = emptyList())
        provider.onUploadDocument = { throw AssertionError("Uploaded a document that was in step.") }
        assertEquals(synced, step.synchronize(provider, base = synced, keptFileNames = emptyList()))
    }

    private fun sync(preferences: FakeUserPreferencesRepository, library: FakeLibraryFileLocalSource) =
        SyncedPreferencesSync(userPreferencesRepository = preferences, libraryFileLocalSource = library)

    private fun library(vararg songs: String) = FakeLibraryFileLocalSource(
        files = songs.associate { SyncKey(kind = LibraryFileKind.SONG, name = it) to it.encodeToByteArray() },
    )

    private fun remoteDocumentOf(provider: FakeSyncProvider) =
        SyncedPreferencesDocument.decode(provider.documents.getValue(SyncedPreferencesDocument.FILE_NAME).first)!!

    private fun encoded(songs: String) = SyncedPreferencesDocument.encode(document(songs))

    /** A document holding [songs] and nothing else, as this version writes one. */
    private fun document(songs: String) = JsonObject(mapOf("version" to JsonPrimitive(1), "songs" to Json.parseToJsonElement(songs)))

    private fun merge(base: String?, local: String, remote: String) = SyncedPreferencesDocument.merge(
        base = base?.let(::document),
        local = document(local),
        remote = document(remote),
    )
}
