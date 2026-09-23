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

import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The editor's draft is a copy against the process ending, so every way of it not being there has to read as none. */
class EditorDraftLocalSourceTest {

    private val root: File = Files.createTempDirectory("campfire-editor-draft").toFile()
    private val fileStorage = JvmFileStorage(root)
    private val localSource = EditorDraftLocalSourceImpl(fileStorage)

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `no file is no draft`() = runBlocking {
        assertNull(localSource.loadEditorDraft())
    }

    @Test
    fun `a saved draft is read back as it was written`() = runBlocking {
        val draft = SongContent(fileName = "катюша.cho", text = "{title: Катюша}\n[Am]Расцвета́ли 100% яблони и груши\r\n\n")

        localSource.saveEditorDraft(draft)

        assertEquals(draft, localSource.loadEditorDraft())
    }

    @Test
    fun `saving no draft removes the file, and does not mind there being none`() = runBlocking {
        localSource.saveEditorDraft(null)
        localSource.saveEditorDraft(SongContent(fileName = "a.cho", text = "text"))
        assertTrue(fileStorage.exists(StorageDirectory.PREFERENCES, FILE_NAME))

        localSource.saveEditorDraft(null)

        assertFalse(fileStorage.exists(StorageDirectory.PREFERENCES, FILE_NAME))
        assertNull(localSource.loadEditorDraft())
    }

    @Test
    fun `a document that does not decode is no draft`() = runBlocking {
        fileStorage.writeText(StorageDirectory.PREFERENCES, FILE_NAME, "{")

        assertNull(localSource.loadEditorDraft())
    }

    @Test
    fun `the draft is kept next to the preferences and never in the library`() = runBlocking {
        localSource.saveEditorDraft(SongContent(fileName = "a.cho", text = "text"))

        assertEquals(listOf(FILE_NAME), fileStorage.listNames(StorageDirectory.PREFERENCES))
        StorageDirectory.entries.filter { it != StorageDirectory.PREFERENCES }.forEach { directory ->
            assertTrue(fileStorage.listNames(directory).isEmpty(), "$directory")
        }
    }

    private companion object {
        const val FILE_NAME = "editor-draft.json"
    }
}
