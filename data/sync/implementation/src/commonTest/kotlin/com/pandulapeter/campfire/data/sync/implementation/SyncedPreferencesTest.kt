/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteWriteResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `a song reset here keeps the field another device changed`() = assertEquals(
        document("""{"a":{"capo":3}}"""),
        merge(
            base = """{"a":{"transposition":2}}""",
            local = """{}""",
            remote = """{"a":{"transposition":2,"capo":3}}""",
        ),
    )

    @Test
    fun `a song reset elsewhere keeps the field changed here`() = assertEquals(
        document("""{"a":{"capo":3}}"""),
        merge(
            base = """{"a":{"transposition":2}}""",
            local = """{"a":{"transposition":2,"capo":3}}""",
            remote = """{}""",
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
    fun `a value this version cannot read is carried over from the base`() = assertEquals(
        document("""{"a.cho":{"tempo":400}}"""),
        SyncedPreferencesDocument.localDocument(base = document("""{"a.cho":{"tempo":400,"capo":2}}"""), preferences = SyncedPreferences()),
    )

    @Test
    fun `a value set here replaces one this version cannot read`() = assertEquals(
        document("""{"a.cho":{"tempo":120,"capo":2}}"""),
        SyncedPreferencesDocument.localDocument(
            base = document("""{"a.cho":{"tempo":400,"capo":2}}"""),
            preferences = SyncedPreferences(tempos = mapOf("a.cho" to 120), capos = mapOf("a.cho" to 2)),
        ),
    )

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

    // The chords

    private fun chordsDocument(chords: String) = JsonObject(
        mapOf("version" to JsonPrimitive(1), "songs" to JsonObject(emptyMap()), "chords" to Json.parseToJsonElement(chords)),
    )

    @Test
    fun `two devices that chose shapes for different chords both keep their choice, and this device's wins for one chord`() = assertEquals(
        chordsDocument("""{"guitar":{"F:0.4.7":"x x 3 2 1 1","G:0.4.7":"3 2 0 0 3 3","C:0.4.7":"x 3 5 5 5 3"}}"""),
        SyncedPreferencesDocument.merge(
            base = chordsDocument("""{"guitar":{"C:0.4.7":"x 3 2 0 1 0"}}"""),
            local = chordsDocument("""{"guitar":{"C:0.4.7":"x 3 5 5 5 3","F:0.4.7":"x x 3 2 1 1"}}"""),
            remote = chordsDocument("""{"guitar":{"C:0.4.7":"8 10 10 9 8 8","G:0.4.7":"3 2 0 0 3 3"}}"""),
        ),
    )

    @Test
    fun `a choice beats a reset`() = assertEquals(
        chordsDocument("""{"guitar":{"C:0.4.7":"x 3 5 5 5 3"}}"""),
        SyncedPreferencesDocument.merge(
            base = chordsDocument("""{"guitar":{"C:0.4.7":"x 3 2 0 1 0"}}"""),
            local = chordsDocument("""{}"""),
            remote = chordsDocument("""{"guitar":{"C:0.4.7":"x 3 5 5 5 3"}}"""),
        ),
    )

    @Test
    fun `an instrument each side took a different shape off is left out, and settling it again changes nothing`() {
        val merged = SyncedPreferencesDocument.merge(
            base = chordsDocument("""{"guitar":{"F":"a","G":"b"}}"""),
            local = chordsDocument("""{"guitar":{"G":"b"}}"""),
            remote = chordsDocument("""{"guitar":{"F":"a"}}"""),
        )
        assertEquals(chordsDocument("""{}"""), merged)
        assertEquals(
            merged,
            SyncedPreferencesDocument.merge(
                base = merged,
                local = SyncedPreferencesDocument.localDocument(base = merged, preferences = SyncedPreferences()),
                remote = merged,
            ),
        )
    }

    @Test
    fun `an instrument still holding a shape is kept, one this version cannot read included`() = assertEquals(
        chordsDocument("""{"guitar":{"C":"c"},"banjo":{"G":7}}"""),
        SyncedPreferencesDocument.merge(
            base = chordsDocument("""{"guitar":{"F":"a","C":"c"},"banjo":{"G":7}}"""),
            local = chordsDocument("""{"guitar":{"C":"c"},"banjo":{"G":7}}"""),
            remote = chordsDocument("""{"guitar":{"F":"a","C":"c"},"banjo":{"G":7}}"""),
        ),
    )

    @Test
    fun `the chords are read and written beside the songs, what this version cannot read passing through`() {
        val base = chordsDocument("""{"guitar":{"C:0.4.7":"x 3 2 0 1 0","D:0.4.7":7},"banjo":{"G:0.4.7":"0 0 0 0 0"}}""")
        assertEquals(
            SyncedPreferences(chords = mapOf("guitar" to mapOf("C:0.4.7" to "x 3 2 0 1 0"), "banjo" to mapOf("G:0.4.7" to "0 0 0 0 0"))),
            SyncedPreferencesDocument.preferencesOf(base),
        )
        assertEquals(
            chordsDocument("""{"guitar":{"D:0.4.7":7,"F:0.4.7":"x x 3 2 1 1"},"banjo":{"G:0.4.7":"0 0 0 0 0"}}"""),
            SyncedPreferencesDocument.localDocument(
                base = base,
                preferences = SyncedPreferences(chords = mapOf("guitar" to mapOf("F:0.4.7" to "x x 3 2 1 1"), "banjo" to mapOf("G:0.4.7" to "0 0 0 0 0"))),
            ),
        )
        assertEquals(document("""{}"""), SyncedPreferencesDocument.localDocument(base = document("""{}"""), preferences = SyncedPreferences()))
    }

    @Test
    fun `a chord's choice is applied value by value, and never dropped with a song`() {
        val preferences = defaultUserPreferences().copy(chordVoicings = mapOf("guitar" to mapOf("C:0.4.7" to "x 3 2 0 1 0", "A:0.4.7" to "x 0 2 2 2 0")))
        val applied = SyncedPreferences(chords = mapOf("guitar" to mapOf("C:0.4.7" to "x 3 5 5 5 3"), "keyboard" to mapOf("C:0.4.7" to "4 7 12"))).applyTo(
            preferences = preferences,
            since = SyncedPreferences(chords = mapOf("guitar" to mapOf("C:0.4.7" to "x 3 2 0 1 0"))),
        )
        assertEquals(
            mapOf("guitar" to mapOf("C:0.4.7" to "x 3 5 5 5 3", "A:0.4.7" to "x 0 2 2 2 0"), "keyboard" to mapOf("C:0.4.7" to "4 7 12")),
            applied.chordVoicings,
        )
        val document = chordsDocument("""{"guitar":{"C:0.4.7":"x 3 5 5 5 3"}}""")
        assertEquals(document["chords"], SyncedPreferencesDocument.withSongsWhere(document) { false }["chords"])
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
        sync(preferences, library("a.cho")).synchronize(
            provider,
            base = document("""{"gone.cho":{"tempo":140}}"""),
            keptFileNames = emptyList(),
        )
        assertEquals(SyncedPreferences(capos = mapOf("a.cho" to 1)), SyncedPreferences.of(preferences.current))
        assertEquals(SyncedPreferences(capos = mapOf("a.cho" to 1)), SyncedPreferencesDocument.preferencesOf(remoteDocumentOf(provider)))
    }

    @Test
    fun `a song another device added during the run keeps its preferences on both sides`() = runTest {
        val provider = FakeSyncProvider()
        provider.documents[SyncedPreferencesDocument.FILE_NAME] = encoded("""{"a.cho":{"capo":1},"s.cho":{"capo":3}}""") to "r1"
        val preferences = FakeUserPreferencesRepository(defaultUserPreferences(capos = mapOf("a.cho" to 1)))
        val synced = sync(preferences, library("a.cho")).synchronize(
            provider,
            base = document("""{"a.cho":{"capo":1}}"""),
            keptFileNames = emptyList(),
        )
        assertEquals(mapOf("a.cho" to 1, "s.cho" to 3), SyncedPreferencesDocument.preferencesOf(remoteDocumentOf(provider)).capos)
        assertTrue("s.cho" in SyncedPreferencesDocument.songNamesOf(synced))
    }

    @Test
    fun `a song added elsewhere is kept on the first run too`() = runTest {
        val provider = FakeSyncProvider()
        provider.documents[SyncedPreferencesDocument.FILE_NAME] = encoded("""{"a.cho":{"capo":1},"s.cho":{"capo":3}}""") to "r1"
        val preferences = FakeUserPreferencesRepository(defaultUserPreferences(capos = mapOf("a.cho" to 1)))
        val synced = sync(preferences, library("a.cho")).synchronize(provider, base = null, keptFileNames = emptyList())
        assertEquals(mapOf("a.cho" to 1, "s.cho" to 3), SyncedPreferencesDocument.preferencesOf(remoteDocumentOf(provider)).capos)
        assertTrue("s.cho" in SyncedPreferencesDocument.songNamesOf(synced))
    }

    @Test
    fun `a song added elsewhere is still kept after a conflict`() = runTest {
        val provider = FakeSyncProvider()
        provider.documents[SyncedPreferencesDocument.FILE_NAME] = encoded("""{"a.cho":{"capo":1},"s.cho":{"capo":3}}""") to "r1"
        var hasWrittenElsewhere = false
        provider.onUploadDocument = {
            if (!hasWrittenElsewhere) {
                hasWrittenElsewhere = true
                provider.documents[SyncedPreferencesDocument.FILE_NAME] =
                    encoded("""{"a.cho":{"capo":1},"s.cho":{"capo":3},"b.cho":{"tempo":90}}""") to "r9"
            }
        }
        // A change made here, so that the first attempt uploads and meets the document written in between.
        val preferences = FakeUserPreferencesRepository(defaultUserPreferences(capos = mapOf("a.cho" to 2)))
        sync(preferences, library("a.cho", "b.cho")).synchronize(
            provider,
            base = document("""{"a.cho":{"capo":1}}"""),
            keptFileNames = emptyList(),
        )
        val folder = SyncedPreferencesDocument.preferencesOf(remoteDocumentOf(provider))
        assertEquals(mapOf("a.cho" to 2, "s.cho" to 3), folder.capos)
        assertEquals(mapOf("b.cho" to 90), folder.tempos)
    }

    @Test
    fun `a song added elsewhere and gone by the next run is pruned then`() = runTest {
        val provider = FakeSyncProvider()
        provider.documents[SyncedPreferencesDocument.FILE_NAME] = encoded("""{"a.cho":{"capo":1},"s.cho":{"capo":3}}""") to "r1"
        val preferences = FakeUserPreferencesRepository(defaultUserPreferences(capos = mapOf("a.cho" to 1)))
        val synchronization = sync(preferences, library("a.cho"))
        val firstBase = synchronization.synchronize(provider, base = document("""{"a.cho":{"capo":1}}"""), keptFileNames = emptyList())
        synchronization.synchronize(provider, base = firstBase, keptFileNames = emptyList())
        assertEquals(mapOf("a.cho" to 1), SyncedPreferencesDocument.preferencesOf(remoteDocumentOf(provider)).capos)
        assertEquals(mapOf("a.cho" to 1), preferences.current.capos)
    }

    @Test
    fun `a song the run could not move keeps its preferences`() = runTest {
        val provider = FakeSyncProvider()
        provider.documents[SyncedPreferencesDocument.FILE_NAME] = encoded("""{"Big.cho":{"tempo":140}}""") to "r1"
        val preferences = FakeUserPreferencesRepository()
        sync(preferences, library()).synchronize(provider, base = null, keptFileNames = listOf("big.cho"))
        assertEquals(mapOf("big.cho" to 140), preferences.current.tempos)
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

    @Test
    fun `a document deleted from the folder removes nothing and is written again`() = runTest {
        val provider = FakeSyncProvider()
        val preferences = FakeUserPreferencesRepository(defaultUserPreferences(capos = mapOf("a.cho" to 2)))
        sync(preferences, library("a.cho")).synchronize(provider, base = document("""{"a.cho":{"capo":2}}"""), keptFileNames = emptyList())
        assertEquals(mapOf("a.cho" to 2), preferences.current.capos)
        assertEquals(mapOf("a.cho" to 2), SyncedPreferencesDocument.preferencesOf(remoteDocumentOf(provider)).capos)
    }

    @Test
    fun `a document that does not parse removes nothing and is replaced`() = assertUnreadableDocumentIsReplaced(
        bytes = """{"version":1,"songs":{"a.cho":{"capo":2},}}""".encodeToByteArray(),
    )

    @Test
    fun `a document whose songs are not an object removes nothing`() = assertUnreadableDocumentIsReplaced(
        bytes = """{"version":1,"songs":[]}""".encodeToByteArray(),
    )

    @Test
    fun `a document of a newer format is left alone`() = runTest {
        val provider = FakeSyncProvider()
        provider.documents[SyncedPreferencesDocument.FILE_NAME] = """{"version":2,"songs":{}}""".encodeToByteArray() to "r1"
        provider.onUploadDocument = { throw AssertionError("Wrote over a document of a newer format.") }
        val preferences = FakeUserPreferencesRepository(defaultUserPreferences(capos = mapOf("a.cho" to 2)))
        val base = document("""{"a.cho":{"capo":2}}""")
        val synced = sync(preferences, library("a.cho")).synchronize(provider, base = base, keptFileNames = emptyList())
        assertEquals(base, synced)
        assertEquals(mapOf("a.cho" to 2), preferences.current.capos)
        assertEquals("r1", provider.documents.getValue(SyncedPreferencesDocument.FILE_NAME).second)
    }

    @Test
    fun `a document of a newer format with no base yet is not a failure`() = runTest {
        val provider = FakeSyncProvider()
        provider.documents[SyncedPreferencesDocument.FILE_NAME] = """{"version":2,"songs":{}}""".encodeToByteArray() to "r1"
        provider.onUploadDocument = { throw AssertionError("Wrote over a document of a newer format.") }
        val preferences = FakeUserPreferencesRepository(defaultUserPreferences(capos = mapOf("a.cho" to 2)))
        assertEquals(
            JsonObject(emptyMap()),
            sync(preferences, library("a.cho")).synchronize(provider, base = null, keptFileNames = emptyList()),
        )
    }

    @Test
    fun `a song the base does not name keeps what is set here against an emptied folder`() = runTest {
        val provider = FakeSyncProvider()
        provider.documents[SyncedPreferencesDocument.FILE_NAME] = encoded("""{}""") to "r1"
        val preferences = FakeUserPreferencesRepository(defaultUserPreferences(capos = mapOf("a.cho" to 2)))
        sync(preferences, library("a.cho")).synchronize(provider, base = document("""{}"""), keptFileNames = emptyList())
        assertEquals(mapOf("a.cho" to 2), preferences.current.capos)
        assertEquals(mapOf("a.cho" to 2), SyncedPreferencesDocument.preferencesOf(remoteDocumentOf(provider)).capos)
    }

    @Test
    fun `a return to the values the run wrote is a change again`() = runTest {
        val provider = FakeSyncProvider()
        provider.documents[SyncedPreferencesDocument.FILE_NAME] = encoded("""{"a.cho":{"capo":3}}""") to "r1"
        val preferences = FakeUserPreferencesRepository()
        val step = sync(preferences, library("a.cho"))
        var changes = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { step.localChanges.collect { changes++ } }
        val synced = step.synchronize(provider, base = null, keptFileNames = emptyList())
        assertEquals(mapOf("a.cho" to 3), preferences.current.capos)
        assertEquals(0, changes)
        preferences.updateUserPreferences { it.copy(capos = mapOf("a.cho" to 4)) }
        assertEquals(1, changes)
        step.synchronize(provider, base = synced, keptFileNames = emptyList())
        preferences.updateUserPreferences { it.copy(capos = mapOf("a.cho" to 3)) }
        assertEquals(2, changes)
    }

    @Test
    fun `an entry spelled differently in the folder applies to this device's file`() = runTest {
        val provider = FakeSyncProvider()
        provider.documents[SyncedPreferencesDocument.FILE_NAME] = encoded("""{"Foo.cho":{"capo":3}}""") to "r1"
        val preferences = FakeUserPreferencesRepository(defaultUserPreferences(transpositions = mapOf("foo.cho" to 2)))
        sync(preferences, library("foo.cho")).synchronize(provider, base = null, keptFileNames = emptyList())
        assertEquals(SyncedPreferences(transpositions = mapOf("foo.cho" to 2), capos = mapOf("foo.cho" to 3)), SyncedPreferences.of(preferences.current))
        assertEquals(document("""{"Foo.cho":{"capo":3,"transposition":2}}"""), remoteDocumentOf(provider))
    }

    @Test
    fun `two spellings of one song in the folder are collapsed`() = runTest {
        val provider = FakeSyncProvider()
        provider.documents[SyncedPreferencesDocument.FILE_NAME] = encoded("""{"Foo.cho":{"capo":3},"foo.cho":{"tempo":90}}""") to "r1"
        val preferences = FakeUserPreferencesRepository()
        sync(preferences, library("foo.cho")).synchronize(provider, base = null, keptFileNames = emptyList())
        assertEquals(SyncedPreferences(tempos = mapOf("foo.cho" to 90), capos = mapOf("foo.cho" to 3)), SyncedPreferences.of(preferences.current))
        assertEquals(document("""{"Foo.cho":{"capo":3,"tempo":90}}"""), remoteDocumentOf(provider))
    }

    @Test
    fun `a value already stored under the folder's spelling moves onto this device's file`() = runTest {
        val provider = FakeSyncProvider()
        val stored = """{"Foo.cho":{"capo":3},"foo.cho":{"transposition":2}}"""
        provider.documents[SyncedPreferencesDocument.FILE_NAME] = encoded(stored) to "r1"
        val preferences = FakeUserPreferencesRepository(
            defaultUserPreferences(transpositions = mapOf("foo.cho" to 2), capos = mapOf("Foo.cho" to 3)),
        )
        sync(preferences, library("foo.cho")).synchronize(provider, base = document(stored), keptFileNames = emptyList())
        assertEquals(SyncedPreferences(transpositions = mapOf("foo.cho" to 2), capos = mapOf("foo.cho" to 3)), SyncedPreferences.of(preferences.current))
        assertEquals(document("""{"Foo.cho":{"capo":3,"transposition":2}}"""), remoteDocumentOf(provider))
    }

    private fun assertUnreadableDocumentIsReplaced(bytes: ByteArray) = runTest {
        val provider = FakeSyncProvider()
        provider.documents[SyncedPreferencesDocument.FILE_NAME] = bytes to "r1"
        val preferences = FakeUserPreferencesRepository(defaultUserPreferences(capos = mapOf("a.cho" to 2)))
        sync(preferences, library("a.cho")).synchronize(provider, base = document("""{"a.cho":{"capo":2}}"""), keptFileNames = emptyList())
        assertEquals(mapOf("a.cho" to 2), preferences.current.capos)
        assertEquals(mapOf("a.cho" to 2), SyncedPreferencesDocument.preferencesOf(remoteDocumentOf(provider)).capos)
    }

    private fun sync(preferences: FakeUserPreferencesRepository, library: FakeLibraryFileLocalSource) =
        SyncedPreferencesSync(userPreferencesRepository = preferences, libraryFileLocalSource = library, logger = Logger.Standard)

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
