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

import androidx.lifecycle.SavedStateHandle
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SavedDialogTest {

    private val song = Song(
        fileName = "a.cho", title = "A", artist = "", key = null, transpose = 0, tags = emptyList(), languages = emptyList(),
        coverArtUrl = null, hasChords = true, canUpdateFileName = false, lastModified = 0, size = 0,
    )
    private val setlist = Setlist(
        fileName = "s.setlist.json",
        title = "S",
        description = "",
        date = LocalDate(2026, 1, 1),
        isArchived = false,
        entries = emptyList(),
        size = 0,
    )
    private val file = SongEditTarget.File("a.cho")
    private val draft = SongEditTarget.EditorDraft("a.cho")

    @Test
    fun `the form sheets are saved by their file names`() {
        assertEquals(SavedDialog.NewSong, DialogType.NewSong.toSavedDialog())
        assertEquals(SavedDialog.NewSetlist, DialogType.NewSetlist.toSavedDialog())
        assertEquals(SavedDialog.EditSetlist("s.setlist.json"), DialogType.EditSetlist(setlist).toSavedDialog())
        assertEquals(SavedDialog.DuplicateSetlist("s.setlist.json"), DialogType.DuplicateSetlist(setlist).toSavedDialog())
        assertEquals(SavedDialog.SongPicker("s.setlist.json"), DialogType.SongPicker(setlist).toSavedDialog())
        assertEquals(
            SavedDialog.SetlistPicker(songFileName = "a.cho", setlistFileName = "s.setlist.json"),
            DialogType.SetlistPicker(song, "s.setlist.json").toSavedDialog(),
        )
    }

    @Test
    fun `a song edit sheet is saved with its kind, its target and its setlist`() {
        fun edit(kind: SavedDialog.SongEdit.Kind, isEditorDraft: Boolean = false, setlistFileName: String? = null) =
            SavedDialog.SongEdit(kind = kind, songFileName = "a.cho", isEditorDraft = isEditorDraft, setlistFileName = setlistFileName)
        assertEquals(edit(SavedDialog.SongEdit.Kind.TAGS), DialogType.SongTags(song, file).toSavedDialog())
        assertEquals(edit(SavedDialog.SongEdit.Kind.TAGS, isEditorDraft = true), DialogType.SongTags(song, draft).toSavedDialog())
        assertEquals(edit(SavedDialog.SongEdit.Kind.LANGUAGES), DialogType.SongLanguages(song, file).toSavedDialog())
        assertEquals(edit(SavedDialog.SongEdit.Kind.METADATA), DialogType.SongMetadata(song, emptyMap(), file).toSavedDialog())
        assertEquals(edit(SavedDialog.SongEdit.Kind.LINKS, isEditorDraft = true), DialogType.SongLinks(song, emptyList(), draft).toSavedDialog())
        assertEquals(
            edit(SavedDialog.SongEdit.Kind.PLAYING, setlistFileName = "s.setlist.json"),
            DialogType.SongPlaying(song, "s.setlist.json", file, emptyMap()).toSavedDialog(),
        )
        assertEquals(edit(SavedDialog.SongEdit.Kind.COVER_ART), DialogType.CoverArtSearch(song, file).toSavedDialog())
        assertEquals(draft, edit(SavedDialog.SongEdit.Kind.TAGS, isEditorDraft = true).target)
        assertEquals(file, edit(SavedDialog.SongEdit.Kind.TAGS).target)
    }

    @Test
    fun `confirmations, the export screen, the tuner and sheets with nothing typed are not saved`() {
        listOf(
            DialogType.DeleteLibrary,
            DialogType.UnsavedChanges,
            DialogType.ConfirmExit,
            DialogType.Export(song = song),
            DialogType.Tuner,
            DialogType.DeleteSong(song),
            DialogType.SongInfo(song),
            DialogType.RemoveSongCoverArt(song, file),
        ).forEach { assertNull(it.toSavedDialog(), it.toString()) }
    }

    @Test
    fun `every saved sheet survives the saved state`() = runTest {
        val store = SavedStateStore(SavedStateHandle(), backgroundScope)
        listOf(
            SavedDialog.NewSong,
            SavedDialog.NewSetlist,
            SavedDialog.EditSetlist("s.setlist.json"),
            SavedDialog.DuplicateSetlist("s.setlist.json"),
            SavedDialog.SongPicker("s.setlist.json"),
            SavedDialog.SetlistPicker(songFileName = "a.cho", setlistFileName = null),
            SavedDialog.SongEdit(SavedDialog.SongEdit.Kind.PLAYING, songFileName = "a.cho", isEditorDraft = false, setlistFileName = "s.setlist.json"),
        ).forEach { saved ->
            store.persist<SavedDialog?>(SavedStateStore.VISIBLE_DIALOG_KEY, saved)
            assertEquals(saved, store.restore<SavedDialog?>(SavedStateStore.VISIBLE_DIALOG_KEY))
        }
        store.persist<SavedDialog?>(SavedStateStore.VISIBLE_DIALOG_KEY, null)
        assertNull(store.restore<SavedDialog?>(SavedStateStore.VISIBLE_DIALOG_KEY))
    }
}
