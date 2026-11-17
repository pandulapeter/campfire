/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.songText

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.edit.ChordProAttachments
import com.pandulapeter.campfire.chordpro.model.ChordProAttachment
import com.pandulapeter.campfire.data.model.image.ImageIntake
import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.data.model.library.LibraryFileSizes
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.domain.api.export.ExportAttachmentUseCase
import com.pandulapeter.campfire.domain.api.images.SaveSongAttachmentsResult
import com.pandulapeter.campfire.domain.api.images.SaveSongAttachmentsUseCase
import com.pandulapeter.campfire.presentation.ui.ChordProTagWriter
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import com.pandulapeter.campfire.presentation.ui.dialogs.forms.songAttachmentsToSave
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.pictures.AttachmentIntake
import com.pandulapeter.campfire.presentation.ui.pictures.PictureWork
import com.pandulapeter.campfire.presentation.ui.platform.FileOpener
import com.pandulapeter.campfire.presentation.ui.rendering.testSongRenderer
import com.pandulapeter.campfire.presentation.ui.state.editor.EditorTextEdit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SongAttachmentEditingTest {

    private val edits = mutableListOf<EditorTextEdit>()
    private val files = FakeSongFiles(mapOf(FILE to "{title: A}"))
    private val exported = mutableMapOf<String, ExportedFile>()

    @Test
    fun `the sheet's attachments replace the ones it offered over the editor, and one synced in meanwhile stays`() = runTest {
        val offered = ChordProAttachment(source = "0000000000000001.pdf", name = "Old")
        val synced = ChordProAttachment(source = "0000000000000002.mp3")
        val saved = ChordProAttachment(source = "0000000000000003.mp4", name = "New")
        editing().save(SongEditTarget.EditorDraft(FILE), attachments = listOf(saved), offered = listOf(offered))
        val text = ChordProAttachments.setAttachments("{title: A}", listOf(offered, synced))
        assertEquals(listOf(saved, synced), ChordProParser.parse(edits.single().edit(text)).metadata.attachments)
    }

    @Test
    fun `the sheet's attachments are written into the file, whose held text follows`() = runTest {
        val attachment = ChordProAttachment(source = "0000000000000001.pdf", name = "Sheet")
        val store = files.songTextStore(backgroundScope, MessageSink(backgroundScope))
        store.loadSongContent(FILE)
        runCurrent()
        editing(store).save(SongEditTarget.File(FILE), attachments = listOf(attachment), offered = emptyList())
        runCurrent()
        assertEquals(listOf(attachment), ChordProParser.parse(files.files.getValue(FILE)).metadata.attachments)
        assertEquals(files.files[FILE], store.songTexts.value[FILE])
    }

    @Test
    fun `an attachment nothing opens is said so`() = runTest {
        val sink = MessageSink(backgroundScope)
        val editing = editing(messageSink = sink)
        editing.open(ChordProAttachment(source = "0000000000000001.gp5"), name = "Solo", opener = FileOpener { false })
        runCurrent()
        assertEquals("Solo.gp5", exported.keys.single())
        assertEquals(listOf<Message>(Message.AttachmentNotOpened), sink.messageQueue.value.map { it.value })
    }

    @Test
    fun `a draft that names the files the song has, under the same names, writes nothing`() {
        val offered = listOf(ChordProAttachment(source = "0000000000000001.pdf", name = "Sheet"), ChordProAttachment(source = "0000000000000002.mp3"))
        assertNull(songAttachmentsToSave(listOf(offered[0].copy(name = " Sheet "), offered[1].copy(name = " ")), offered))
        assertEquals(offered.reversed(), songAttachmentsToSave(offered.reversed(), offered))
    }

    private fun TestScope.editing(
        store: SongTextStore? = null,
        messageSink: MessageSink = MessageSink(backgroundScope),
    ): SongAttachmentEditing {
        val songTextStore = store ?: files.songTextStore(backgroundScope, messageSink)
        val metadataEditing = SongMetadataEditing(
            dialogHost = DialogHost(backgroundScope),
            messageSink = messageSink,
            songTextStore = songTextStore,
            songRenderer = testSongRenderer(),
            editorNotation = { UserPreferences.Notation.STANDARD },
            retainedEditorField = { null },
            editorDraft = { null },
            emitEditorTextEdit = { edits += it },
            setChordProTag = ChordProTagWriter,
        )
        return SongAttachmentEditing(
            scope = backgroundScope,
            messageSink = messageSink,
            songTextStore = songTextStore,
            songMetadataEditing = metadataEditing,
            intake = AttachmentIntake(addDraftAttachment = { _, _ -> ImageIntake.Accepted("0000000000000009.pdf") }, messageSink = messageSink, work = PictureWork()),
            saveSongAttachments = object : SaveSongAttachmentsUseCase {
                override suspend fun invoke(fileName: String, expectedText: String, attachments: List<ChordProAttachment>): SaveSongAttachmentsResult {
                    val text = ChordProAttachments.setAttachments(expectedText, attachments)
                    files.files[fileName] = text
                    return SaveSongAttachmentsResult.Saved(text)
                }
            },
            exportAttachment = object : ExportAttachmentUseCase {
                override suspend fun invoke(source: String, name: String) =
                    ExportedFile(name = "$name.${source.substringAfterLast('.')}", mimeType = "", bytes = ByteArray(1)).also { exported[it.name] = it }
            },
            librarySizes = MutableStateFlow<LibraryFileSizes?>(null),
        )
    }

    private companion object {
        const val FILE = "song.cho"
    }
}
