/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.ChordProTime
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_album
import com.pandulapeter.campfire.presentation.resources.ic_info
import com.pandulapeter.campfire.presentation.resources.ic_label
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.ic_link
import com.pandulapeter.campfire.presentation.resources.song_details_change_cover_art
import com.pandulapeter.campfire.presentation.resources.song_details_languages_edit
import com.pandulapeter.campfire.presentation.resources.song_details_links_edit
import com.pandulapeter.campfire.presentation.resources.song_details_metadata_edit
import com.pandulapeter.campfire.presentation.resources.song_details_set_cover_art
import com.pandulapeter.campfire.presentation.resources.song_details_song_info
import com.pandulapeter.campfire.presentation.resources.song_details_tags_manage
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenuItem
import com.pandulapeter.campfire.presentation.ui.metronome.EffectiveTempo
import com.pandulapeter.campfire.presentation.ui.metronome.timeSignatureOrDefault
import org.jetbrains.compose.resources.painterResource

/**
 * The way into the sheet of what the song is, on the song details screen: a button of its own wherever the app bar has
 * the room, and the first entry of its menu wherever it does not. Outside performance mode it is there for every song,
 * since the sheet is where what the song says about itself is edited from; in it, only where there is something to
 * read. [isEnabled] is whether the song's text, which the sheet is read from, is at hand.
 */
@Composable
internal fun songInfoAction(
    viewModel: CampfireViewModel,
    song: Song,
    isEnabled: Boolean,
) = ActionsMenuItem(
    title = stringResource(Res.string.song_details_song_info),
    icon = painterResource(Res.drawable.ic_info),
    isEnabled = isEnabled,
    onClick = { viewModel.showDialog(CampfireViewModel.DialogType.SongInfo(song)) },
)

/**
 * The controls of the four values a song is played by, for one page of the song details pager: built where that page
 * is drawn, so that each song of a setlist sets its own. The caller leaves them out in read only mode, where the
 * values are read rather than set.
 *
 * @param tempo Passed in rather than read here, since the screen already holds it for the app bar's metronome button.
 */
@Composable
internal fun rememberSongPlayingControls(
    viewModel: CampfireViewModel,
    song: Song,
    setlistFileName: String?,
    transposition: Int,
    chordSpelling: UserPreferences.ChordSpelling,
    tempo: EffectiveTempo,
    capo: EffectiveCapo,
    canTranspose: Boolean,
): SongPlayingControls {
    val key = if (canTranspose) viewModel.renderKey(song = song, transposition = transposition, spelling = chordSpelling) else null
    val timeSignature = song.timeSignatureOrDefault.toString()
    val isTimeDeclared = ChordProTime.parse(song.time) != null
    val fileName = song.fileName
    return remember(
        viewModel,
        fileName,
        setlistFileName,
        canTranspose,
        transposition,
        key,
        capo,
        tempo,
        timeSignature,
        isTimeDeclared,
        song,
    ) {
        SongPlayingControls(
            key = if (canTranspose) {
                SongKeyControl(
                    transposition = transposition,
                    key = key,
                    onStep = { viewModel.stepTransposition(fileName, setlistFileName, it) },
                    onReset = { viewModel.resetTransposition(fileName, setlistFileName) },
                )
            } else {
                null
            },
            capo = SongCapoControl(
                capo = capo,
                onStep = { viewModel.stepCapo(fileName, setlistFileName, it) },
                onReset = { viewModel.resetCapo(fileName, setlistFileName) },
            ),
            tempo = SongTempoControl(
                tempo = tempo,
                onStep = { viewModel.stepTempo(fileName, setlistFileName, it) },
                onTapped = { viewModel.setTempo(fileName, setlistFileName, it) },
                onReset = { viewModel.resetTempo(fileName, setlistFileName) },
            ),
            time = SongTimeControl(
                signature = timeSignature,
                isDeclared = isTimeDeclared,
                onClick = { viewModel.showDialog(CampfireViewModel.DialogType.SongTimeSignature(song = song, time = song.time)) },
            ),
        )
    }
}

/**
 * What the header and group edit buttons open: the dialog of each group, on the song details screen's sheet and on the
 * editor preview's card alike. From the editor ([isEditorDraft]) they edit the text being typed rather than the file,
 * see [CampfireViewModel.DialogType.SongEdit], and [song] is that text's description of the song, which follows every
 * keystroke, so the latest one is what a button opens its dialog on.
 */
@Composable
internal fun rememberSongInfoEditing(
    viewModel: CampfireViewModel,
    song: Song,
    isEditorDraft: Boolean,
): SongInfoEditing {
    val latestSong by rememberUpdatedState(song)
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val isCoverArtEnabled = userPreferences?.isCoverArtEnabled == true
    return remember(viewModel, isEditorDraft, isCoverArtEnabled) {
        SongInfoEditing(
            onEditCoverArt = if (isCoverArtEnabled) {
                { viewModel.showSongCoverArtDialog(song = latestSong, isEditorDraft = isEditorDraft) }
            } else null,
            onEditMetadata = { viewModel.showSongMetadataDialog(song = latestSong, isEditorDraft = isEditorDraft) },
            onEditTags = { viewModel.showSongTagsDialog(song = latestSong, isEditorDraft = isEditorDraft) },
            onEditLanguages = { viewModel.showSongLanguagesDialog(song = latestSong, isEditorDraft = isEditorDraft) },
            onEditLinks = { viewModel.showSongLinksDialog(song = latestSong, isEditorDraft = isEditorDraft) },
        )
    }
}

/**
 * [SongInfoEditing] as entries of the editor and song details menus. In the order of the card's groups, and kept
 * behind the overflow tap however much room the bar has.
 */
@Composable
internal fun songInfoEditingActions(editing: SongInfoEditing): List<ActionsMenuItem> = listOf(
    ActionsMenuItem(
        title = stringResource(Res.string.song_details_metadata_edit),
        icon = painterResource(Res.drawable.ic_info),
        isAlwaysInMenu = true,
        onClick = editing.onEditMetadata,
    ),
    ActionsMenuItem(
        title = stringResource(Res.string.song_details_tags_manage),
        icon = painterResource(Res.drawable.ic_label),
        isAlwaysInMenu = true,
        onClick = editing.onEditTags,
    ),
    ActionsMenuItem(
        title = stringResource(Res.string.song_details_languages_edit),
        icon = painterResource(Res.drawable.ic_language),
        isAlwaysInMenu = true,
        onClick = editing.onEditLanguages,
    ),
    ActionsMenuItem(
        title = stringResource(Res.string.song_details_links_edit),
        icon = painterResource(Res.drawable.ic_link),
        isAlwaysInMenu = true,
        onClick = editing.onEditLinks,
    ),
)

/**
 * Opens the cover art sheet from the editor or song details menu.
 */
@Composable
internal fun coverArtAction(
    viewModel: CampfireViewModel,
    song: Song,
    isEditorDraft: Boolean,
) = ActionsMenuItem(
    title = stringResource(if (song.coverArtUrl == null) Res.string.song_details_set_cover_art else Res.string.song_details_change_cover_art),
    icon = painterResource(Res.drawable.ic_album),
    isAlwaysInMenu = true,
    onClick = { viewModel.showSongCoverArtDialog(song = song, isEditorDraft = isEditorDraft) },
)

/**
 * The tag and the language editors as entries of a song card's menu on the songs screen: unlike the metadata and the
 * link dialogs they need nothing but the song list's own metadata to open, and filing songs under a tag or a language
 * is done to many of them in a row, which a trip to every song's details screen makes a chore.
 */
@Composable
internal fun songLabelActions(
    viewModel: CampfireViewModel,
    song: Song,
    isEditorDraft: Boolean,
): List<ActionsMenuItem> = listOf(
    ActionsMenuItem(
        title = stringResource(Res.string.song_details_tags_manage),
        icon = painterResource(Res.drawable.ic_label),
        isAlwaysInMenu = true,
        onClick = { viewModel.showDialog(CampfireViewModel.DialogType.SongTags(song = song, isEditorDraft = isEditorDraft)) },
    ),
    ActionsMenuItem(
        title = stringResource(Res.string.song_details_languages_edit),
        icon = painterResource(Res.drawable.ic_language),
        isAlwaysInMenu = true,
        onClick = { viewModel.showDialog(CampfireViewModel.DialogType.SongLanguages(song = song, isEditorDraft = isEditorDraft)) },
    ),
)
