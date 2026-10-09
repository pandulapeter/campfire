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

import kotlinx.serialization.Serializable

/**
 * The form sheet that was up as Android saved the process's state, by kind and file names only: [DialogType] carries
 * domain models and snapshots taken as the sheet opened, which are neither serializable nor worth saving, since the
 * sheet is reopened through its ordinary opener and its own saved fields find it again.
 *
 * Only the sheets something is typed or picked into have one. Every confirmation is left out, since putting back a
 * question nobody answered is worse than losing it, and `DeleteLibrary` above all, whose typed answer must never outlive
 * the moment; so are the export screen, which has a pipeline of its own, the tuner, which only ever listens from a tap,
 * the welcome and what's new, which the start decides on by itself, and the sheets that hold nothing typed (Song info,
 * Chord shapes, the song filters).
 */
@Serializable
internal sealed interface SavedDialog {

    @Serializable
    data object NewSong : SavedDialog

    @Serializable
    data object NewSetlist : SavedDialog

    @Serializable
    data class EditSetlist(val setlistFileName: String) : SavedDialog

    @Serializable
    data class DuplicateSetlist(val setlistFileName: String) : SavedDialog

    @Serializable
    data class SongPicker(val setlistFileName: String) : SavedDialog

    @Serializable
    data class SetlistPicker(val songFileName: String, val setlistFileName: String?) : SavedDialog

    /**
     * One of the [DialogType.SongEdit] sheets.
     *
     * @param isEditorDraft Whether it edits the editor's text ([SongEditTarget.EditorDraft]) rather than the file.
     * @param setlistFileName The setlist a Song defaults sheet names the overrides of, see [DialogType.SongPlaying].
     */
    @Serializable
    data class SongEdit(
        val kind: Kind,
        val songFileName: String,
        val isEditorDraft: Boolean,
        val setlistFileName: String?,
    ) : SavedDialog {

        enum class Kind { TAGS, LANGUAGES, METADATA, LINKS, PLAYING, COVER_ART }

        val target: SongEditTarget
            get() = if (isEditorDraft) SongEditTarget.EditorDraft(songFileName) else SongEditTarget.File(songFileName)
    }
}

/** What of this dialog is worth reopening in a restored process, see [SavedDialog]; null for everything else. */
internal fun DialogType.toSavedDialog(): SavedDialog? = when (this) {
    DialogType.NewSong -> SavedDialog.NewSong
    DialogType.NewSetlist -> SavedDialog.NewSetlist
    is DialogType.EditSetlist -> SavedDialog.EditSetlist(setlist.fileName)
    is DialogType.DuplicateSetlist -> SavedDialog.DuplicateSetlist(setlist.fileName)
    is DialogType.SongPicker -> SavedDialog.SongPicker(setlist.fileName)
    is DialogType.SetlistPicker -> SavedDialog.SetlistPicker(songFileName = song.fileName, setlistFileName = setlistFileName)
    is DialogType.SongTags -> savedSongEdit(SavedDialog.SongEdit.Kind.TAGS)
    is DialogType.SongLanguages -> savedSongEdit(SavedDialog.SongEdit.Kind.LANGUAGES)
    is DialogType.SongMetadata -> savedSongEdit(SavedDialog.SongEdit.Kind.METADATA)
    is DialogType.SongLinks -> savedSongEdit(SavedDialog.SongEdit.Kind.LINKS)
    is DialogType.SongPlaying -> savedSongEdit(SavedDialog.SongEdit.Kind.PLAYING, setlistFileName)
    is DialogType.CoverArtSearch -> savedSongEdit(SavedDialog.SongEdit.Kind.COVER_ART)
    else -> null
}

private fun DialogType.SongEdit.savedSongEdit(kind: SavedDialog.SongEdit.Kind, setlistFileName: String? = null) = SavedDialog.SongEdit(
    kind = kind,
    songFileName = target.fileName,
    isEditorDraft = target is SongEditTarget.EditorDraft,
    setlistFileName = setlistFileName,
)
