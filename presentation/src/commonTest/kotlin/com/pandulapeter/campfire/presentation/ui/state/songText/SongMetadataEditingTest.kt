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

import androidx.compose.foundation.text.input.TextFieldState
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.edit.ChordProLinks
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields.Field
import com.pandulapeter.campfire.chordpro.edit.ChordProTags
import com.pandulapeter.campfire.chordpro.model.ChordProLink
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.model.domain.normalizedToNfc
import com.pandulapeter.campfire.domain.api.useCases.SetChordProTagUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.rendering.testSongRenderer
import com.pandulapeter.campfire.presentation.ui.screens.songEditor.EditorTextEdit
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Every edit is aimed at the editor's draft, which hands it over as a function rather than writing a file, so that a
 * test can apply it to a text of its choosing - the file as a sync run left it while the sheet was open. The use cases
 * are stood in for by the `:chordpro` objects their implementations delegate to.
 */
class SongMetadataEditingTest {

    private val edits = mutableListOf<EditorTextEdit>()
    private var notation = UserPreferences.Notation.STANDARD
    private var retainedField: TextFieldState? = null
    private var draft: SongContent? = null
    private val draftTarget = SongEditTarget.EditorDraft(FILE)

    @Test
    fun `a tag the sheet offered and left unticked is taken off, and one it never offered stays`() = runTest {
        editing().setSongTags(draftTarget, tags = listOf("Pop"), offeredTags = listOf("rock", "Pop"))
        val text = "{title: A}\n{tag: Rock}\n{tag: Synced}"
        assertEquals(setOf("Synced", "Pop"), ChordProParser.parse(editedText(text)).metadata.tags.toSet())
    }

    @Test
    fun `a decomposed tag the sheet offered and left unticked is taken off`() = runTest {
        editing().setSongTags(draftTarget, tags = emptyList(), offeredTags = listOf("Ünnep"))
        // The decomposed letter is spelled as an escape, since a literal one would not survive a tool that normalizes text.
        val text = "{title: A}\n{tag: U\u0308nnep}"
        assertEquals(emptyList(), ChordProParser.parse(editedText(text)).metadata.tags)
    }

    @Test
    fun `a link the sheet never offered stays after the ones it saved`() = runTest {
        val offered = ChordProLink(url = "https://a.example")
        val synced = ChordProLink(url = "https://synced.example")
        val saved = ChordProLink(url = "https://b.example")
        editing().setSongLinks(draftTarget, links = listOf(saved), offeredLinks = listOf(offered))
        val text = ChordProLinks.setLinks("{title: A}", listOf(offered, synced))
        assertEquals(listOf(saved.url, synced.url), ChordProParser.parse(editedText(text)).metadata.links.map { it.url })
    }

    @Test
    fun `only the details changed in the sheet are written, and a field left as offered keeps what a sync run brought in`() = runTest {
        editing().setSongMetadata(
            draftTarget,
            values = mapOf(Field.ALBUM to "New", Field.COMPOSER to " X "),
            offeredValues = mapOf(Field.ALBUM to "Old", Field.COMPOSER to "X"),
        )
        val metadata = ChordProParser.parse(editedText("{title: A}\n{album: Old}\n{composer: Y}")).metadata
        assertEquals("New", metadata.album)
        assertEquals("Y", metadata.composer)
    }

    @Test
    fun `details that differ from what was offered only by surrounding spaces write nothing`() = runTest {
        val editing = editing()
        editing.setSongMetadata(draftTarget, values = mapOf(Field.ALBUM to " Old "), offeredValues = mapOf(Field.ALBUM to "Old"))
        editing.setSongPlaying(draftTarget, values = mapOf(Field.TEMPO to "120 "), offeredValues = mapOf(Field.TEMPO to "120"))
        assertEquals(emptyList(), edits)
    }

    @Test
    fun `a key typed in the German notation is written in the standard one`() = runTest {
        notation = UserPreferences.Notation.GERMAN
        editing().setSongPlaying(draftTarget, values = mapOf(Field.KEY to "B"), offeredValues = mapOf(Field.KEY to ""))
        assertEquals("Bb", ChordProParser.parse(editedText("{title: A}")).metadata.key)
    }

    @Test
    fun `the draft's text is read from the editor's field first, then from the draft it last reported for that file`() = runTest {
        val editing = editing()
        draft = SongContent(fileName = FILE, text = "reported")
        assertEquals("reported", editing.songTextOf(draftTarget))
        retainedField = TextFieldState("typed")
        assertEquals("typed", editing.songTextOf(draftTarget))
        retainedField = null
        draft = SongContent(fileName = "other.cho", text = "another song's draft")
        assertNull(editing.songTextOf(draftTarget))
    }

    /** What the one edit handed to the editor makes of [text]. */
    private fun editedText(text: String) = edits.single().also { assertEquals(FILE, it.fileName) }.edit(text)

    private fun TestScope.editing(): SongMetadataEditing {
        val messageSink = MessageSink(backgroundScope)
        return SongMetadataEditing(
            dialogHost = DialogHost(backgroundScope),
            messageSink = messageSink,
            songTextStore = FakeSongFiles().songTextStore(backgroundScope, messageSink),
            songRenderer = testSongRenderer(),
            editorNotation = { notation },
            retainedEditorField = { fileName -> retainedField.takeIf { fileName == FILE } },
            editorDraft = { draft },
            emitEditorTextEdit = { edits += it },
            setChordProTag = object : SetChordProTagUseCase {
                override fun invoke(text: String, tag: String, isSelected: Boolean) = if (isSelected) {
                    ChordProTags.addTag(text, tag, fold = String::normalizedToNfc)
                } else {
                    ChordProTags.removeTag(text, tag, fold = String::normalizedToNfc)
                }
            },
        )
    }

    private companion object {
        const val FILE = "song.cho"
    }
}
