/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import androidx.compose.foundation.text.input.TextFieldState
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.ChordProTempo
import com.pandulapeter.campfire.chordpro.model.ChordProLink
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.model.domain.normalizedTags
import com.pandulapeter.campfire.domain.api.useCases.ParseChordProUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProCoverArtUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProLanguagesUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProLinksUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProMetadataUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProTagUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.SONG_METADATA_FIELDS
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.rendering.SongRenderer
import com.pandulapeter.campfire.presentation.ui.screens.songEditor.EditorTextEdit
import com.pandulapeter.campfire.presentation.ui.songInfo.hasSongInfo
import kotlinx.coroutines.flow.map

/**
 * The sheets that each write one part of what a song says about itself - its tags, languages, details, links, cover
 * and song defaults - into the file, or into the editor's text where the editor is what they were opened from.
 *
 * @param editorNotation The notation the editor's field is written in, see `CampfireViewModel.editorNotation`.
 * @param retainedEditorField The open editor's field, see `CampfireViewModel.retainedEditorField`.
 * @param editorDraft The open editor's text as it last reported it.
 * @param emitEditorTextEdit Hands an edit to the open editor, see `CampfireViewModel.editorTextEdits`.
 */
internal class SongMetadataEditing(
    private val dialogHost: DialogHost,
    private val messageSink: MessageSink,
    private val songTextStore: SongTextStore,
    private val songRenderer: SongRenderer,
    private val editorNotation: () -> UserPreferences.Notation,
    private val retainedEditorField: (fileName: String) -> TextFieldState?,
    private val editorDraft: () -> SongContent?,
    private val emitEditorTextEdit: (EditorTextEdit) -> Unit,
    private val parseChordPro: ParseChordProUseCase,
    private val setChordProCoverArt: SetChordProCoverArtUseCase,
    private val setChordProLanguages: SetChordProLanguagesUseCase,
    private val setChordProTag: SetChordProTagUseCase,
    private val setChordProLinks: SetChordProLinksUseCase,
    private val setChordProMetadata: SetChordProMetadataUseCase,
) {

    /**
     * Makes [tags] the tags a song carries, from the tag dialog: every one of [offeredTags] the dialog left unticked is
     * taken off and every ticked one put on, compared without regard to case, as the file's tags always are. The file
     * is rewritten once for the whole set, as [setSongLanguages] writes one. What it carries is read from the text the
     * edit is built on rather than from the list entry the dialog was opened with, so a tag another device synced in
     * while the dialog was open, and which it therefore never offered, is left on.
     */
    fun setSongTags(target: SongEditTarget, tags: List<String>, offeredTags: List<String>) {
        val keptKeys = tags.mapTo(mutableSetOf()) { it.lowercase() }
        val offeredKeys = offeredTags.mapTo(mutableSetOf()) { it.lowercase() }
        editSong(target) { text ->
            val removed = parseChordPro(text).metadata.tags.filter { it.lowercase() in offeredKeys && it.lowercase() !in keptKeys }
            val withoutRemoved = removed.fold(text) { current, tag -> setChordProTag(text = current, tag = tag, isSelected = false) }
            tags.fold(withoutRemoved) { current, tag -> setChordProTag(text = current, tag = tag, isSelected = true) }
        }
    }

    /**
     * Declares the languages of a song, from the header of the screen that is playing it. The whole set arrives at
     * once rather than one language at a time, because the picker asks for all of them before it is closed and a
     * file the user owns is better rewritten once than once per checkbox.
     */
    fun setSongLanguages(target: SongEditTarget, codes: List<String>) = editSong(target) { text ->
        setChordProLanguages(text = text, codes = codes)
    }

    /** Opens the label pickers on the current draft when invoked from the editor. */
    fun showSongTagsDialog(song: Song, target: SongEditTarget) {
        val currentSong = songForLabelEditing(song, target) ?: return
        dialogHost.showDialog(DialogType.SongTags(song = currentSong, target = target))
    }

    fun showSongLanguagesDialog(song: Song, target: SongEditTarget) {
        val currentSong = songForLabelEditing(song, target) ?: return
        dialogHost.showDialog(DialogType.SongLanguages(song = currentSong, target = target))
    }

    private fun songForLabelEditing(song: Song, target: SongEditTarget): Song? {
        if (target is SongEditTarget.File) return song
        val metadata = parseChordPro(songTextOf(target) ?: return null).metadata
        return song.copy(tags = normalizedTags(metadata.tags), languages = metadata.languages)
    }

    /**
     * Opens the metadata editor on the source text, since the album, the composer and the rest are not part of the
     * song list's lighter metadata, and the title there already has the subtitle in it.
     */
    fun showSongMetadataDialog(song: Song, target: SongEditTarget) {
        val metadata = parseChordPro(songTextOf(target) ?: return).metadata
        dialogHost.showDialog(
            DialogType.SongMetadata(
                song = song,
                values = SONG_METADATA_FIELDS.associateWith { ChordProMetadataFields.valueOf(metadata, it).orEmpty() },
                target = target,
            )
        )
    }

    /**
     * Writes the fields of the metadata dialog that were changed there, and only those: a field another device changed
     * while the dialog was open, and which the user left as it was offered, keeps the other device's value.
     */
    fun setSongMetadata(
        target: SongEditTarget,
        values: Map<ChordProMetadataFields.Field, String>,
        offeredValues: Map<ChordProMetadataFields.Field, String>,
    ) {
        val changed = values.filter { (field, value) -> value.trim() != offeredValues[field]?.trim() }
        if (changed.isNotEmpty()) editSong(target) { text -> setChordProMetadata(text = text, values = changed) }
    }

    /** Opens the link editor on the source text, since links are not part of the song list's lighter metadata. */
    fun showSongLinksDialog(song: Song, target: SongEditTarget) {
        val text = songTextOf(target) ?: return
        dialogHost.showDialog(DialogType.SongLinks(song = song, links = parseChordPro(text).metadata.links, target = target))
    }

    /** What [text] says about the song beyond its lines, for the sheet of what the song is (see [hasSongInfo] for the button opening it). */
    fun songMetadataOf(text: String): ChordProMetadata = parseChordPro(text).metadata

    /**
     * Whether the sheet of what the song is has anything to show for [text], from a scan of its directives alone: none
     * of what the sheet shows is changed by the notation a full parse brings the chords into, and the scan is a
     * fraction of the parse, which matters on the frame of a swipe that makes another song the current one.
     */
    fun hasSongInfo(text: String): Boolean = ChordProParser.parseMetadata(text).hasSongInfo

    /**
     * Writes the link dialog's changes together. Links added by sync while it was open and never offered there stay
     * in the file, as tags do: a snapshot of one dialog is not a request to erase another device's additions.
     */
    fun setSongLinks(target: SongEditTarget, links: List<ChordProLink>, offeredLinks: List<ChordProLink>) {
        val offeredUrls = offeredLinks.mapTo(mutableSetOf()) { it.url }
        editSong(target) { text ->
            val addedElsewhere = parseChordPro(text).metadata.links.filterNot { it.url in offeredUrls }
            setChordProLinks(text = text, links = links + addedElsewhere)
        }
    }

    /** Opens the cover sheet with the cover currently declared in the editor's text. */
    fun showSongCoverArtDialog(song: Song, target: SongEditTarget) {
        val currentSong = when (target) {
            is SongEditTarget.File -> song
            is SongEditTarget.EditorDraft -> song.copy(coverArtUrl = parseChordPro(songTextOf(target) ?: return).metadata.coverArt)
        }
        dialogHost.showDialog(DialogType.CoverArtSearch(song = currentSong, target = target))
    }

    /**
     * Makes [url] the song's cover, or takes the cover off for null, from the cover search sheet. Written into the
     * file like a tag is, so that the cover travels with the song wherever it goes.
     */
    fun setSongCoverArt(target: SongEditTarget, url: String?) = editSong(target) { text ->
        setChordProCoverArt(text = text, url = url)
    }

    /**
     * The text a metadata dialog is built on: the editor's own while it is the editor's draft the dialog edits, since
     * that is what its edit is applied to, and the file's otherwise.
     */
    fun songTextOf(target: SongEditTarget) = when (target) {
        // Read the field itself: draft reporting runs asynchronously and can still be one edit behind a tap.
        is SongEditTarget.EditorDraft ->
            retainedEditorField(target.fileName)?.text?.toString() ?: editorDraft()?.takeIf { it.fileName == target.fileName }?.text
        is SongEditTarget.File -> songTextStore.songTexts.value[target.fileName]
    }

    /** Writes [edit] into the file, or hands it to the editor where the dialog asking for it edits the editor's draft. */
    private fun editSong(target: SongEditTarget, edit: (String) -> String) {
        when (target) {
            is SongEditTarget.EditorDraft -> emitEditorTextEdit(EditorTextEdit(fileName = target.fileName, edit = edit))
            is SongEditTarget.File -> messageSink.launchLibraryChange { songTextStore.editSongText(target.fileName, edit) }
        }
    }

    /**
     * Opens the "Song defaults" sheet on what the file declares for the four values the song is played by, the key in
     * the reader's notation (the way the editor's field shows it) and the tempo as the number the click reads out of it.
     * [setlistFileName] is where the song is being read, whose overrides the sheet names and can take back. Opened from
     * the editor, it reads and changes the text being typed there instead, which is read through no setlist.
     */
    fun showSongPlayingDialog(song: Song, setlistFileName: String?, target: SongEditTarget) {
        val metadata = parseChordPro(songTextOf(target) ?: return).metadata
        dialogHost.showDialog(
            DialogType.SongPlaying(
                // The sheet's key field notes a {transpose} the text opens with, which the draft may have changed since
                // the editor last read it.
                song = if (target is SongEditTarget.EditorDraft) song.copy(transpose = metadata.transpose) else song,
                setlistFileName = setlistFileName.takeIf { target is SongEditTarget.File },
                target = target,
                values = mapOf(
                    ChordProMetadataFields.Field.KEY to metadata.key?.takeIf { it.isNotBlank() }?.let { songRenderer.editorKeyOf(it, editorNotation()) }.orEmpty(),
                    ChordProMetadataFields.Field.CAPO to metadata.capo?.toString().orEmpty(),
                    ChordProMetadataFields.Field.TEMPO to ChordProTempo.parse(metadata.tempo)?.toString().orEmpty(),
                    ChordProMetadataFields.Field.TIME to metadata.time?.takeIf { it.isNotBlank() }.orEmpty(),
                ),
            )
        )
    }

    /**
     * Writes the fields of the "Song defaults" sheet that were changed there, and only those, as [setSongMetadata]
     * does. The key is typed in the reader's notation and written in the standard one, converted on its own rather
     * than with the file around it, which is already in the standard notation; a blank value takes a directive off.
     */
    fun setSongPlaying(
        target: SongEditTarget,
        values: Map<ChordProMetadataFields.Field, String>,
        offeredValues: Map<ChordProMetadataFields.Field, String>,
    ) {
        val changed = values
            .filter { (field, value) -> value.trim() != offeredValues[field]?.trim() }
            .mapValues { (field, value) -> if (field == ChordProMetadataFields.Field.KEY) fileKeyOf(value) else value }
        if (changed.isNotEmpty()) editSong(target) { text -> setChordProMetadata(text = text, values = changed) }
    }

    /** A key typed in the editor's notation, as the file is to hold it; the inverse of [SongRenderer.editorKeyOf]. */
    private fun fileKeyOf(key: String) = key.trim().takeIf { it.isNotEmpty() }?.let { typed ->
        parseChordPro(songRenderer.fileTextOf("{key: $typed}", editorNotation())).metadata.key ?: typed
    }.orEmpty()
}
