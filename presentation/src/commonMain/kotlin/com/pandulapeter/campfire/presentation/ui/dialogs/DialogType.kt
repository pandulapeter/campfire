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

import com.pandulapeter.campfire.chordpro.ChordProMetadataFields
import com.pandulapeter.campfire.chordpro.model.ChordProLink
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel

sealed interface DialogType {
    data class Export(val song: Song? = null, val setlist: Setlist? = null, val songSetlistFileName: String? = null) : DialogType
    data object NewSetlist : DialogType
    data object NewSong : DialogType
    data object SongFilters : DialogType
    /**
     * Every setlist with a box each, which is how a song is both put into one and taken out of another.
     * [setlistFileName] is the setlist the song is being read through, if any, whose box is shown but cannot be
     * changed: the song is taken out of a setlist from the setlist's own row, not from the screen reading it there.
     */
    data class SetlistPicker(val song: Song, val setlistFileName: String? = null) : DialogType
    /**
     * Every song of the library with a box each, which is how a setlist is filled from its own side rather than
     * one song at a time from the menu of each. [setlist] is the setlist the sheet was opened on, and only stands
     * in for the one in [CampfireViewModel.setlists] until the library has caught up with it, which a setlist created a
     * moment ago may not have.
     */
    data class SongPicker(val setlist: Setlist) : DialogType
    data class DeleteSetlist(val setlist: Setlist) : DialogType
    data class RemoveSongFromSetlist(val songFileName: String, val songTitle: String, val setlistFileName: String) : DialogType
    data class EditSetlist(val setlist: Setlist) : DialogType
    data class DuplicateSetlist(val setlist: Setlist) : DialogType
    data class DeleteSong(val song: Song) : DialogType
    /**
     * What a song says about itself beyond how it is played, opened from the song details app bar. It reads the
     * song's text as it is now rather than a snapshot, so that what its buttons edit is there when it is opened
     * again.
     */
    data class SongInfo(val song: Song) : DialogType
    /**
     * The chords of a song and the other ways each can be played, opened from the header of its Chords section,
     * which reads the song as the page plays it - in the setlist it was opened from, if any - and its text as it
     * is now.
     */
    data class ChordShapes(val song: Song, val setlistFileName: String?) : DialogType
    /**
     * Opened from the song details overflow menu, and offers the song's own tags and
     * the rest of the library's.
     */
    data class SongTags(override val song: Song, override val isEditorDraft: Boolean = false) : SongEdit
    /** A snapshot of what the song says for each field the overflow menu's metadata editor offers, blank for nothing. */
    data class SongMetadata(override val song: Song, val values: Map<ChordProMetadataFields.Field, String>, override val isEditorDraft: Boolean = false) : SongEdit
    /** A snapshot of the links offered by the overflow menu's link editor. */
    data class SongLinks(override val song: Song, val links: List<ChordProLink>, override val isEditorDraft: Boolean = false) : SongEdit
    /** Opened from the same menu, and asking about every language at once rather than one at a time. */
    data class SongLanguages(override val song: Song, override val isEditorDraft: Boolean = false) : SongEdit

    /**
     * What the song's file declares for the four values it is played by, opened from the song details editing
     * menu or from the editor's: a snapshot of each as the sheet offers it, blank for nothing (see
     * [CampfireViewModel.showSongPlayingDialog]). [setlistFileName] is the setlist the song is read through, whose overrides the
     * sheet names, or null for the library's on this device, and always null for the editor's draft.
     */
    data class SongPlaying(
        override val song: Song,
        val setlistFileName: String?,
        override val isEditorDraft: Boolean = false,
        val values: Map<ChordProMetadataFields.Field, String>,
    ) : SongEdit
    /**
     * The records the song may have come out on, whose front cover can be made the song's, see
     * [CampfireViewModel.searchCoverArt].
     */
    data class CoverArtSearch(override val song: Song, override val isEditorDraft: Boolean = false) : SongEdit
    /** Removing a cover rewrites the file, so the cover art sheet asks before doing it. */
    data class RemoveSongCoverArt(override val song: Song, override val isEditorDraft: Boolean = false) : SongEdit

    /**
     * A dialog that edits the metadata of one song, opened from the song details overflow menu or from the editor's.
     * Opened from the editor, it changes the text being typed there rather than the file (see
     * [CampfireViewModel.editorTextEdits]), since nothing but Save writes the file the editor is open on.
     */
    sealed interface SongEdit : DialogType {
        val song: Song
        val isEditorDraft: Boolean
    }
    /**
     * Asked before the connected account is forgotten. Nothing is deleted either way, but reconnecting means
     * going through the consent page again, which is not something to end up in by mistapping a list row.
     */
    data class DisconnectSync(val accountName: String) : DialogType
    /** Asked before the copies of the covers are deleted, which costs a download of each one shown again. */
    data object ClearCoverArtCache : DialogType
    /**
     * Asked before every song and setlist is deleted, and answered by typing a word rather than by a tap, since it
     * is the one thing in the app that loses the user's own work wholesale.
     */
    data object DeleteLibrary : DialogType
    /**
     * Asked before the editor is left with something in it that has not been written yet, see
     * [CampfireViewModel.navigateBack].
     */
    data object UnsavedChanges : DialogType
    /** Asked before a key that otherwise only goes back closes the application, see [CampfireViewModel.confirmExit]. */
    data object ConfirmExit : DialogType

    /**
     * Asked over the import screen before its answer overwrites [count] files of the library, which is the one
     * answer to its question that cannot be taken back.
     */
    data class ConfirmImportReplace(val count: Int) : DialogType
    /** Asked before the editor throws away everything typed since the last save, see [CampfireViewModel.revertEditorChanges]. */
    data object RevertChanges : DialogType

    /** Shown once, over the first run of an installation, see [CampfireViewModel.showWelcomeOnFirstRun]. */
    data object Welcome : DialogType
    /** The current version's introduction, shown once after the first installed version. */
    data object WhatsNew : DialogType
}
