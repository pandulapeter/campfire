/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields
import com.pandulapeter.campfire.data.model.song.Song
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DialogHostTest {

    private val song = Song(
        fileName = "a.cho",
        title = "A",
        artist = "",
        key = null,
        transpose = 0,
        tags = emptyList(),
        languages = emptyList(),
        coverArtUrl = null,
        hasChords = true,
        canUpdateFileName = false,
        lastModified = 0,
        size = 0,
    )

    @Test
    fun `listeners run in order around the change`() = runTest {
        val host = DialogHost(backgroundScope)
        val calls = mutableListOf<String>()
        host.addBeforeDialogChange { previous, next -> calls += "before 1 $previous $next ${host.visibleDialog.value}" }
        host.addBeforeDialogChange { _, _ -> calls += "before 2" }
        host.addAfterDialogChange { _, next -> calls += "after $next ${host.visibleDialog.value}" }
        host.showDialog(DialogType.ConfirmExit)
        assertEquals(
            listOf("before 1 null ConfirmExit null", "before 2", "after ConfirmExit ConfirmExit"),
            calls,
        )
    }

    @Test
    fun `a sheet dismisses only itself`() = runTest {
        val host = DialogHost(backgroundScope)
        host.showDialog(DialogType.UnsavedChanges)
        host.dismissSheet(DialogType.ConfirmExit)
        assertEquals(DialogType.UnsavedChanges, host.visibleDialog.value)
        host.dismissSheet(DialogType.UnsavedChanges)
        assertNull(host.visibleDialog.value)
    }

    @Test
    fun `the song info sheet stays open under an edit of its own song and comes back after it`() = runTest {
        val host = DialogHost(backgroundScope)
        val info = DialogType.SongInfo(song = song, target = file)
        val tags = DialogType.SongTags(song = song, target = file)
        host.showDialog(info)
        host.showDialog(tags)
        assertEquals(listOf(info, tags), host.openDialogs.value)
        host.dismissDialog()
        assertEquals(info, host.visibleDialog.value)
        assertEquals(listOf<DialogType>(info), host.openDialogs.value)
    }

    @Test
    fun `the song info sheet of the file does not stay under an edit of the editor's text`() = runTest {
        val host = DialogHost(backgroundScope)
        host.showDialog(DialogType.SongInfo(song = song, target = file))
        host.showDialog(DialogType.SongTags(song = song, target = draft))
        assertEquals(1, host.openDialogs.value.size)
    }

    @Test
    fun `a dialog that stacks over nothing open replaces the whole stack`() = runTest {
        val host = DialogHost(backgroundScope)
        host.showDialog(DialogType.SongInfo(song = song, target = file))
        host.showDialog(DialogType.SongTags(song = song, target = file))
        host.showDialog(DialogType.ConfirmExit)
        assertEquals(listOf<DialogType>(DialogType.ConfirmExit), host.openDialogs.value)
        host.dismissDialog()
        assertEquals(emptyList(), host.openDialogs.value)
    }

    @Test
    fun `the lookup fills the form under it in place, and closing it uncovers that form`() = runTest {
        val host = DialogHost(backgroundScope)
        val info = DialogType.SongInfo(song = song, target = file)
        val form = DialogType.SongMetadata(song = song, values = emptyMap(), target = file)
        val lookup = DialogType.SongDetailsLookup(song = song, target = file, values = emptyMap(), draft = emptyMap(), formFillCount = 0)
        val filled = form.copy(draft = mapOf(ChordProMetadataFields.Field.ALBUM to "Nimrod"))
        host.showDialog(info)
        host.showDialog(form)
        host.showDialog(lookup)
        assertEquals(listOf(info, form, lookup), host.openDialogs.value)
        host.updateUnderlyingDialog(filled)
        assertEquals(listOf(info, filled, lookup), host.openDialogs.value)
        host.dismissSheet(lookup)
        assertEquals(filled, host.visibleDialog.value)
        assertEquals(listOf(info, filled), host.openDialogs.value)
    }

    @Test
    fun `a confirmed removal closes the question and asks the cover sheet under it to close itself`() = runTest {
        val host = DialogHost(backgroundScope)
        val info = DialogType.SongInfo(song = song, target = file)
        val cover = DialogType.CoverArtSearch(song = song, target = file)
        host.showDialog(info)
        host.showDialog(cover)
        host.showDialog(DialogType.RemoveSongCoverArt(song = song, target = file))
        host.dismissDialogAndSheetUnder()
        assertEquals(listOf(info, cover), host.openDialogs.value)
        assertEquals(cover, host.closeRequest.value)
        host.dismissSheet(cover)
        assertEquals(listOf<DialogType>(info), host.openDialogs.value)
        assertNull(host.closeRequest.value)
    }

    @Test
    fun `the stack listeners see what was open before and after`() = runTest {
        val host = DialogHost(backgroundScope)
        val info = DialogType.SongInfo(song = song, target = file)
        val tags = DialogType.SongTags(song = song, target = file)
        val changes = mutableListOf<Pair<List<DialogType>, List<DialogType>>>()
        host.addOpenDialogsChange { before, after -> changes += before to after }
        host.showDialog(info)
        host.showDialog(tags)
        host.dismissDialog()
        assertEquals(
            listOf(
                emptyList<DialogType>() to listOf<DialogType>(info),
                listOf<DialogType>(info) to listOf(info, tags),
                listOf(info, tags) to listOf<DialogType>(info),
            ),
            changes,
        )
    }

    private val file = SongEditTarget.File(song.fileName)
    private val draft = SongEditTarget.EditorDraft(song.fileName)
}
