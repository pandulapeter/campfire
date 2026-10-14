/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.songs

import com.pandulapeter.campfire.presentation.ui.state.SongTextStore
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.domain.api.useCases.CreateSongUseCase
import com.pandulapeter.campfire.domain.api.useCases.DeleteSongUseCase
import com.pandulapeter.campfire.domain.api.useCases.DuplicateSongUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSongContentUseCase
import com.pandulapeter.campfire.domain.api.useCases.RenameSongFileUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink

/**
 * Creating, duplicating, renaming and deleting a song file, and making everything that holds the file's name in memory
 * follow: the texts read from it, the metronome, the screens opened on it and the import report. The holders it calls are
 * passed in as the narrow functions it uses of them.
 */
internal class SongFilesController(
    private val messageSink: MessageSink,
    private val songTextStore: SongTextStore,
    private val showDialog: (DialogType) -> Unit,
    private val duplicateSongDialog: (song: Song, text: String) -> DialogType,
    private val openEditor: (fileName: String, shouldStartInsideFirstSection: Boolean) -> Unit,
    private val clearEditorDraft: () -> Unit,
    private val followMetronomeRename: (from: String, to: String) -> Unit,
    private val followScreensRename: (from: String, to: String) -> Unit,
    private val closeScreensShowing: (fileName: String) -> Unit,
    private val followReportedFileNames: ((String) -> String?) -> Unit,
    private val getSongContent: GetSongContentUseCase,
    private val createSongFile: CreateSongUseCase,
    private val duplicateSongFile: DuplicateSongUseCase,
    private val renameSongFile: RenameSongFileUseCase,
    private val deleteSongFile: DeleteSongUseCase,
) {

    /** Creates the file and opens it in the editor, which is the only useful thing to do with an empty song. */
    fun createSong(values: Map<ChordProMetadataFields.Field, String>) = messageSink.launchLibraryChange {
        val song = createSongFile(
            title = values[ChordProMetadataFields.Field.TITLE].orEmpty(),
            artist = values[ChordProMetadataFields.Field.ARTIST].orEmpty(),
            metadata = values,
        )
        openEditor(song.fileName, true)
    }

    /**
     * Opens the New song form filled in with what [song] says about itself, read from its file, since the subtitle,
     * the album and the rest are not part of the song list's lighter metadata. The file is read here rather than into
     * the texts the open screens hold, which are all read again on every sync run.
     */
    fun showDuplicateSongDialog(song: Song) = messageSink.launchLibraryChange {
        showDialog(duplicateSongDialogOf(song) ?: return@launchLibraryChange messageSink.sendMessage(Message.OperationFailed))
    }

    internal suspend fun duplicateSongDialogOf(song: Song) = getSongContent(song.fileName)?.let { duplicateSongDialog(song, it.text) }

    /**
     * Writes a copy of [song] with the fields of the form that were changed there written over it, as
     * `setSongMetadata` writes them, and opens the copy in the editor: a copy is made to be changed, a "Live" or an
     * "Acoustic" version of the song, and what makes it one is rarely in its header alone.
     */
    fun duplicateSong(
        song: Song,
        values: Map<ChordProMetadataFields.Field, String>,
        offeredValues: Map<ChordProMetadataFields.Field, String>,
    ) = messageSink.launchLibraryChange {
        val changes = values.filter { (field, value) -> value.trim() != offeredValues[field]?.trim() }
        val copy = duplicateSongFile(fileName = song.fileName, changes = changes)
            ?: return@launchLibraryChange messageSink.sendMessage(Message.OperationFailed)
        openEditor(copy.fileName, false)
    }

    /**
     * Renames the song's file to the one its own metadata gives it, offered by the song's menu wherever the two have
     * drifted apart (`Song.canUpdateFileName`). The use case follows the references that are on disk - the setlists
     * holding the song, its saved transposition - and what is left here is the two places the old name lives in
     * memory: the text read from the file, and the screens that were opened on it. Where a setlist or the
     * transposition could not follow, the rename still stands and the screens still follow it; that is said
     * afterwards (`Message.SongFileRenamedPartly`).
     */
    fun updateSongFileName(song: Song) = messageSink.launchLibraryChange {
        // Before the file moves, so that the moment the library drops the old name is already covered.
        songTextStore.updateSongsBeingRenamed { it + (song.fileName to song) }
        try {
            val rename = renameSongFile(song) ?: return@launchLibraryChange
            val fileName = rename.fileName
            // Only while the screen being rewritten is the one on top, which is what makes sure the context moves off
            // the old name and the entry is taken out again. Before the screens follow: the metronome reads whether
            // the click plays for the old name from the back stack, which must still name it.
            followMetronomeRename(song.fileName, fileName)
            songTextStore.updateSongTexts { texts -> texts[song.fileName]?.let { texts - song.fileName + (fileName to it) } ?: texts }
            followScreensRename(song.fileName, fileName)
            followReportedFileNames { if (it == song.fileName) fileName else it }
            // Said after the screens have followed the file, which has moved whatever else could not be rewritten.
            if (!rename.haveReferencesFollowed) messageSink.sendMessage(Message.SongFileRenamedPartly)
        } finally {
            // Cleared once the screens name the new file - and on the paths that never got that far: a rename that
            // found nothing to do, one that threw, and a view model going away mid-rename.
            songTextStore.updateSongsBeingRenamed { it - song.fileName }
        }
    }

    fun deleteSong(fileName: String) = messageSink.launchLibraryChange {
        val haveReferencesBeenRemoved = deleteSongFile(fileName)
        // Nobody is asked to save a file that has just been deleted, so the draft goes before the screens holding it.
        clearEditorDraft()
        // A screen showing the file that has just gone is closed first, or it would sit there on nothing.
        closeScreensShowing(fileName)
        songTextStore.updateSongTexts { it - fileName }
        followReportedFileNames { it.takeUnless { it == fileName } }
        // Said once the screens have let go of the file, which is gone whatever else could not be rewritten.
        if (!haveReferencesBeenRemoved) messageSink.sendMessage(Message.SongDeletedPartly)
    }
}
