/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.merge

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.sync.implementation.FakeLibraryFileLocalSource
import com.pandulapeter.campfire.data.sync.implementation.FakeSyncProvider
import com.pandulapeter.campfire.data.sync.implementation.index.SyncIndexDocument
import com.pandulapeter.campfire.data.sync.implementation.run.OtherAppLibrary.checklist
import com.pandulapeter.campfire.data.sync.implementation.run.OtherAppLibrary.note
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The merge of one folder document, driven through a notes app's settings ([NotesSettingsCodec]) rather than Campfire's. */
class MergedFolderDocumentTest {

    private val provider = FakeSyncProvider()
    private val codec = NotesSettingsCodec()

    // The three-way merge

    @Test
    fun `two devices that changed different notes both keep their change`() = runTest {
        folder("""{"version":1,"notes":{"a.note":{"pinned":true},"b.note":{"pinned":true}}}""")
        codec.state.value = NotesSettings(pinned = setOf("a.note"), colors = mapOf("a.note" to "red"))

        val settled = document("a", "b").synchronize(base = json("""{"version":1,"notes":{"a.note":{"pinned":true}}}"""))

        val expected = NotesSettings(pinned = setOf("a.note", "b.note"), colors = mapOf("a.note" to "red"))
        assertEquals(expected, codec.state.value)
        assertEquals(expected, codec.valuesOf(folderDocument()))
        assertEquals(folderDocument(), settled?.document)
        assertEquals(provider.documents.getValue(NotesSettingsCodec.FILE_NAME).second, settled?.revision)
    }

    @Test
    fun `a value removed on one side and changed on the other keeps the change`() = runTest {
        folder("""{"version":1,"notes":{"a.note":{"color":"blue"}}}""")
        codec.state.value = NotesSettings()

        document("a").synchronize(base = json("""{"version":1,"notes":{"a.note":{"color":"red"}}}"""))

        assertEquals(mapOf("a.note" to "blue"), codec.state.value?.colors)
    }

    @Test
    fun `a write refused by its revision is merged again with what was written meanwhile`() = runTest {
        folder("""{"version":1,"notes":{}}""")
        var hasWrittenElsewhere = false
        provider.onUploadDocument = {
            if (!hasWrittenElsewhere) {
                hasWrittenElsewhere = true
                provider.writeDocumentElsewhere(NotesSettingsCodec.FILE_NAME, encoded("""{"version":1,"notes":{"b.note":{"pinned":true}}}"""))
            }
        }
        codec.state.value = NotesSettings(pinned = setOf("a.note"))

        val settled = document("a", "b").synchronize(base = json("""{"version":1,"notes":{}}"""))

        val expected = NotesSettings(pinned = setOf("a.note", "b.note"))
        assertEquals(expected, codec.state.value)
        assertEquals(expected, codec.valuesOf(folderDocument()))
        assertEquals(folderDocument(), settled?.document)
    }

    @Test
    fun `a document written elsewhere under every attempt is left for the next run`() = runTest {
        folder("""{"version":1,"notes":{}}""")
        provider.onUploadDocument = {
            provider.writeDocumentElsewhere(NotesSettingsCodec.FILE_NAME, encoded("""{"version":1,"notes":{"b.note":{"pinned":true}}}"""))
        }
        codec.state.value = NotesSettings(pinned = setOf("a.note"))

        assertNull(document("a", "b").synchronize(base = json("""{"version":1,"notes":{}}""")))
        assertEquals(setOf("a.note", "b.note"), codec.state.value?.pinned)
    }

    @Test
    fun `a run's own write is not a change made here`() = runTest {
        folder("""{"version":1,"notes":{"a.note":{"color":"red"}}}""")
        val document = document("a")
        var changes = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { document.localChanges.collect { changes++ } }

        document.synchronize(base = null)
        assertEquals(mapOf("a.note" to "red"), codec.state.value?.colors)
        assertEquals(0, changes)
        codec.state.value = NotesSettings(colors = mapOf("a.note" to "green"))

        assertEquals(1, changes)
    }

    // Documents left alone or replaced

    @Test
    fun `a document past the bounds is replaced with this device's values`() = runTest {
        val nested = "[".repeat(200) + "]".repeat(200)
        provider.documents[NotesSettingsCodec.FILE_NAME] = """{"version":1,"notes":{},"deep":}""".encodeToByteArray() to "r1"
        codec.state.value = NotesSettings(pinned = setOf("a.note"))

        val settled = document("a").synchronize(base = json("""{"version":1,"notes":{"a.note":{"pinned":true}}}"""))

        assertEquals(NotesSettings(pinned = setOf("a.note")), codec.valuesOf(folderDocument()))
        assertNotNull(settled?.revision)
    }

    @Test
    fun `a document of a newer format is neither applied nor written over`() = runTest {
        val newer = encoded("""{"version":2,"notes":{"b.note":{"pinned":true}}}""")
        provider.documents[NotesSettingsCodec.FILE_NAME] = newer to "r1"
        codec.state.value = NotesSettings(pinned = setOf("a.note"))
        val base = json("""{"version":1,"notes":{"a.note":{"pinned":true}}}""")

        val settled = document("a", "b").synchronize(base = base)

        assertEquals(NotesSettings(pinned = setOf("a.note")), codec.state.value)
        assertContentEquals(newer, provider.documents.getValue(NotesSettingsCodec.FILE_NAME).first)
        assertEquals(SettledFolderDocument(document = base, revision = null), settled)
    }

    @Test
    fun `values this version does not own pass through every write`() = runTest {
        folder("""{"version":1,"notes":{"a.note":{"pinned":true,"archived":true}},"font":"serif"}""")
        codec.state.value = NotesSettings(pinned = setOf("a.note"), theme = "dark")

        document("a").synchronize(base = json("""{"version":1,"notes":{"a.note":{"pinned":true,"archived":true}},"font":"serif"}"""))

        assertEquals(
            json("""{"version":1,"notes":{"a.note":{"pinned":true,"archived":true}},"font":"serif","theme":"dark"}"""),
            folderDocument(),
        )
    }

    // The library the entries are keyed by

    @Test
    fun `an entry of a note gone from the library goes while one the run could not move stays`() = runTest {
        val base = json("""{"version":1,"notes":{"a.note":{"pinned":true},"b.note":{"pinned":true},"c.note":{"pinned":true}}}""")
        provider.documents[NotesSettingsCodec.FILE_NAME] = FolderDocumentJson.encode(base) to "r1"
        codec.state.value = NotesSettings(pinned = setOf("a.note", "b.note", "c.note"))

        document("a").synchronize(base = base, keptFileNames = listOf("c.note"))

        assertEquals(setOf("a.note", "c.note"), codec.state.value?.pinned)
        assertEquals(setOf("a.note", "c.note"), codec.keysOf(folderDocument()))
    }

    @Test
    fun `an entry the folder spells differently applies to this device's file`() = runTest {
        folder("""{"version":1,"notes":{"A.note":{"color":"red"}}}""")
        codec.state.value = NotesSettings(pinned = setOf("a.note"))

        document("a").synchronize(base = null)

        assertEquals(NotesSettings(pinned = setOf("a.note"), colors = mapOf("a.note" to "red")), codec.state.value)
        assertEquals(json("""{"version":1,"notes":{"A.note":{"color":"red","pinned":true}}}"""), folderDocument())
    }

    @Test
    fun `only the keyed collection's forgotten files lose their entries from the base`() {
        val base = json("""{"version":1,"notes":{"A.note":{"pinned":true},"b.note":{"pinned":true},"c.list":{"pinned":true}}}""")

        val kept = document().withoutEntriesOf(base, listOf(note("a"), checklist("c")))

        assertEquals(setOf("b.note", "c.list"), codec.keysOf(kept))
    }

    // The base the index keeps

    @Test
    fun `a base keeps only the fields its writer owned`() {
        val stored = json("""{"version":1,"notes":{"a.note":{"pinned":true,"color":"red"},"b.note":{"color":"blue"}},"theme":"dark"}""")

        assertEquals(
            json("""{"version":1,"notes":{"a.note":{"pinned":true}},"theme":"dark"}"""),
            document().baseIn(SyncIndexDocument(syncedPreferences = stored)),
        )
        assertEquals(stored, document().baseIn(SyncIndexDocument(syncedPreferences = stored, syncedPreferencesFields = NotesSettingsCodec.OWNED_FIELDS)))
    }

    @Test
    fun `the index pairs the base with the folder's revision of it`() {
        val stored = json("""{"version":1,"notes":{}}""")
        val index = SyncIndexDocument(accountId = "dropbox:dbid:1", syncedPreferences = stored, syncedPreferencesRevision = "r1")
        val name = NotesSettingsCodec.FILE_NAME

        val unchanged = document().unchangedIn(index, "dropbox:dbid:1", mapOf(name to "r1"))
        assertEquals("r1", unchanged?.revision)
        assertContentEquals(FolderDocumentJson.encode(stored), unchanged?.bytes)
        assertNull(document().unchangedIn(index, "dropbox:dbid:1", mapOf(name to "r2")))
        assertNull(document().unchangedIn(index, "dropbox:dbid:1", emptyMap()))
        assertNull(document().unchangedIn(index, "dropbox:dbid:2", mapOf(name to "r1")))
        assertNull(document().unchangedIn(index.copy(syncedPreferencesRevision = ""), "dropbox:dbid:1", mapOf(name to "")))
        assertNull(document().unchangedIn(index.copy(syncedPreferencesRevision = null), "dropbox:dbid:1", mapOf(name to "r1")))
    }

    private fun document(vararg notes: String) = MergedFolderDocument(
        codec = codec,
        libraryFileLocalSource = FakeLibraryFileLocalSource(files = notes.associate { note(it) to it.encodeToByteArray() }),
        logger = Logger.Standard,
    )

    private suspend fun MergedFolderDocument<*>.synchronize(base: JsonObject?, keptFileNames: List<String> = emptyList()) = synchronize(
        provider = provider,
        base = base,
        unchanged = null,
        keptFileNames = keptFileNames,
        onLocalSettled = {},
    )

    private fun folder(document: String) {
        provider.documents[NotesSettingsCodec.FILE_NAME] = encoded(document) to "r1"
    }

    private fun folderDocument() = assertNotNull(FolderDocumentJson.decode(provider.documents.getValue(NotesSettingsCodec.FILE_NAME).first))

    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    private fun encoded(text: String) = FolderDocumentJson.encode(json(text))
}
