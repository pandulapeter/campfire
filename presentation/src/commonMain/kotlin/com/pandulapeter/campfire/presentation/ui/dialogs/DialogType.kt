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

import com.pandulapeter.campfire.presentation.ui.dialogs.pickers.SetlistPicker
import com.pandulapeter.campfire.presentation.ui.dialogs.pickers.SongPicker
import com.pandulapeter.campfire.presentation.ui.dialogs.forms.toSongMetadataDraft
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields
import com.pandulapeter.campfire.chordpro.model.ChordProLink
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.state.LibraryState
import com.pandulapeter.campfire.presentation.ui.state.online.CoverArtSearchController
import com.pandulapeter.campfire.presentation.ui.state.online.SongDetailsLookupController
import com.pandulapeter.campfire.presentation.ui.state.songs.SongFilesController
import com.pandulapeter.campfire.presentation.ui.chords.SongChord

sealed interface DialogType {
    /**
     * The export screen over one [song], a [setlist], or [songs] ticked in the selection mode, which are several songs in
     * no running order of anyone's making.
     */
    data class Export(
        val song: Song? = null,
        val setlist: Setlist? = null,
        val songSetlistFileName: String? = null,
        val songs: List<Song> = emptyList(),
    ) : DialogType
    data object NewSetlist : DialogType
    data object NewSong : DialogType

    /**
     * The New song form filled in with what [song] says about itself ([values], a snapshot of each field the form
     * offers, blank for nothing), which makes a copy of the song with whatever the user changed there written over it,
     * see [SongFilesController.duplicateSong].
     */
    data class DuplicateSong(val song: Song, val values: Map<ChordProMetadataFields.Field, String>) : DialogType

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
     * in for the one in [LibraryState.setlists] until the library has caught up with it, which a setlist created a
     * moment ago may not have.
     */
    data class SongPicker(val setlist: Setlist) : DialogType
    /**
     * Every setlist with a three-state box for the songs ticked in the selection mode ([songFileNames], in the order the
     * songs screen sorts by), which only ever adds them and takes back only what it added.
     */
    data class SelectionSetlists(val songFileNames: List<String>) : DialogType

    /** The tags of the songs ticked in the selection mode, a row half ticked where only some of them carry it. */
    data class SelectionTags(val songFileNames: List<String>) : DialogType

    /** The languages of the songs ticked in the selection mode, the way [SelectionTags] asks about their tags. */
    data class SelectionLanguages(val songFileNames: List<String>) : DialogType
    data class DeleteSetlist(val setlist: Setlist) : DialogType
    data class RemoveSongFromSetlist(val songFileName: String, val songTitle: String, val setlistFileName: String) : DialogType
    data class EditSetlist(val setlist: Setlist) : DialogType

    /** The calendar of [setlist]'s date alone, opened from its countdown, whose Save writes only the day. */
    data class SetlistDate(val setlist: Setlist) : DialogType

    data class DuplicateSetlist(val setlist: Setlist) : DialogType
    data class DeleteSong(val song: Song) : DialogType
    /**
     * What a song says about itself beyond how it is played, and the one place it is edited: opened from the song
     * details app bar and its menu over the file, and from the editor's menu and its preview's card over the text
     * being typed ([target]). It reads that text as it is now rather than a snapshot, so that what its tiles edit is
     * there when it is opened again.
     */
    data class SongInfo(val song: Song, val target: SongEditTarget) : DialogType
    /**
     * One chord of a song and the other ways it can be played, opened by tapping its diagram in the Chords section,
     * which reads the song as the page plays it - in the setlist it was opened from, if any - and its text as it is
     * now, and finds the chord by the name the page shows it with ([chordName]).
     */
    data class ChordShape(val song: Song, val setlistFileName: String?, val chordName: String) : DialogType

    /**
     * One chord of the Chords tab and the other ways it can be played, the chord sheet without a song around it: the
     * shape it settles on is the player's for the chord, as the song's sheet writes it.
     */
    @ConsistentCopyVisibility
    data class ChordReference internal constructor(internal val chord: SongChord) : DialogType
    /**
     * The instrument in the player's hands and its tuning, opened from the instrument chip of every screen that depends on
     * it (the chips at the top of the song details screen's first section, the Tuner tab, the Chords tab, Settings, and
     * the onboarding), so that it is changed where its effect is seen.
     */
    data object Instrument : DialogType

    /** The tuning of the instrument in the player's hands, opened from the tuning chip next to the instrument's. */
    data object Tuning : DialogType

    /** The notation chords are written in, opened from its chip in Settings. */
    data object Notation : DialogType

    /** The tuner over the song being read, opened from the song's tuner button (or its overflow menu); it listens while it is up. */
    data object Tuner : DialogType
    /**
     * Opened from the song details overflow menu, and offers the song's own tags and
     * the rest of the library's.
     */
    data class SongTags(override val song: Song, override val target: SongEditTarget) : SongEdit
    /**
     * A snapshot of what the song says for each field the metadata editor offers, blank for nothing, and the [draft] the
     * form opens with in its own spelling (see `toSongMetadataDraft`): null for [values] as they are, and otherwise
     * what the form held when the song details lookup was put up over it, with what the lookup filled in. [fillCount] is
     * how many times the song details lookup has filled this form in, so that a fill equal to the one before still puts
     * its draft back into the fields.
     */
    data class SongMetadata(
        override val song: Song,
        val values: Map<ChordProMetadataFields.Field, String>,
        override val target: SongEditTarget,
        val draft: Map<ChordProMetadataFields.Field, String>? = null,
        val fillCount: Int = 0,
    ) : SongEdit
    /** A snapshot of the links offered by the overflow menu's link editor. */
    data class SongLinks(override val song: Song, val links: List<ChordProLink>, override val target: SongEditTarget) : SongEdit
    /** Opened from the same menu, and asking about every language at once rather than one at a time. */
    data class SongLanguages(override val song: Song, override val target: SongEditTarget) : SongEdit

    /**
     * What the song's file declares for the four values it is played by, opened from the song details editing
     * menu or from the editor's: a snapshot of each as the sheet offers it, blank for nothing (see
     * `SongMetadataEditing.showSongPlayingDialog`). [setlistFileName] is the setlist the song is read through, whose overrides the
     * sheet names, or null for the library's on this device, and always null for the editor's draft.
     */
    data class SongPlaying(
        override val song: Song,
        val setlistFileName: String?,
        override val target: SongEditTarget,
        val values: Map<ChordProMetadataFields.Field, String>,
    ) : SongEdit
    /**
     * The records the song may have come out on, whose front cover can be made the song's, see
     * [CoverArtSearchController.searchCoverArt].
     */
    data class CoverArtSearch(override val song: Song, override val target: SongEditTarget) : SongEdit
    /** Removing a cover rewrites the file, so the cover art sheet asks before doing it. */
    data class RemoveSongCoverArt(override val song: Song, override val target: SongEditTarget) : SongEdit
    /**
     * The recordings the song's title names in the music catalogues, put up over the metadata editor, whose [values]
     * and [draft] it carries so that it can put the form back as it was — or with what the user picked filled into the
     * draft, see [SongDetailsLookupController.searchSongDetails]. [formFillCount] is that form's [SongMetadata.fillCount].
     */
    data class SongDetailsLookup(
        override val song: Song,
        override val target: SongEditTarget,
        val values: Map<ChordProMetadataFields.Field, String>,
        val draft: Map<ChordProMetadataFields.Field, String>,
        val formFillCount: Int,
    ) : SongEdit

    /**
     * A dialog that edits the metadata of one song, opened from the song details overflow menu or from the editor's.
     * Opened from the editor, it changes the text being typed there rather than the file (see
     * `EditorSession.editorTextEdits`), since nothing but Save writes the file the editor is open on.
     */
    sealed interface SongEdit : DialogType {
        val song: Song
        val target: SongEditTarget
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
     * `Navigator.navigateBack`.
     */
    data object UnsavedChanges : DialogType
    /** Asked before a key that otherwise only goes back closes the application, see `AppExitController.confirmExit`. */
    data object ConfirmExit : DialogType

    /**
     * Asked over the import screen before its answer overwrites [count] files of the library, which is the one
     * answer to its question that cannot be taken back.
     */
    data class ConfirmImportReplace(val count: Int) : DialogType
    /** Asked before the editor throws away everything typed since the last save, see `EditorSession.revertEditorChanges`. */
    data object RevertChanges : DialogType

    /** The current version's introduction, shown once after the first installed version. */
    data object WhatsNew : DialogType

    /** The languages of the interface, opened from the onboarding's first page so that it can be read from the start. */
    data object AppLanguage : DialogType

    /** What Campfire is built with and under which licenses, opened from Settings → About. */
    data object OpenSourceLicenses : DialogType
}
