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
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_album
import com.pandulapeter.campfire.presentation.resources.ic_info
import com.pandulapeter.campfire.presentation.resources.ic_label
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.ic_link
import com.pandulapeter.campfire.presentation.resources.ic_tune
import com.pandulapeter.campfire.presentation.resources.song_details_change_cover_art
import com.pandulapeter.campfire.presentation.resources.song_details_languages_edit
import com.pandulapeter.campfire.presentation.resources.song_details_links_edit
import com.pandulapeter.campfire.presentation.resources.song_details_metadata_edit
import com.pandulapeter.campfire.presentation.resources.song_details_playing_edit
import com.pandulapeter.campfire.presentation.resources.song_details_set_cover_art
import com.pandulapeter.campfire.presentation.resources.song_details_tags_manage
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenuItem
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import com.pandulapeter.campfire.presentation.ui.playing.EffectiveCapo
import com.pandulapeter.campfire.presentation.ui.playing.EffectiveTempo
import com.pandulapeter.campfire.presentation.ui.metronome.timeSignatureOrDefault
import org.jetbrains.compose.resources.painterResource

/**
 * The controls of the four values a song is played by, for one page of the song details pager: built where that page
 * is drawn, so that each song of a setlist sets its own. The caller leaves them out in read only mode, where the
 * values are read rather than set.
 *
 * @param tempo Passed in rather than read here, since the screen already holds it for the app bar's metronome button.
 * @param shouldShowChords False with the chords switched off, which takes the transposition and the capo with them.
 * @param shouldShowTempo False with the metronome switched off, which takes the tempo and the time signature with it.
 * @return Null where both are switched off, which leaves the song nothing to set.
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
    shouldShowChords: Boolean,
    shouldShowTempo: Boolean,
): SongPlayingControls? {
    if (!shouldShowChords && !shouldShowTempo) return null
    val canTranspose = shouldShowChords && song.hasChords
    // The key the transposition alone takes the song to, which is what the chords on the page spell: the capo is the
    // stepper next to this one and moves the sounding key without moving a chord, so counting it in here would have
    // this control naming a key that is written nowhere in the song. The app bar's is the sounding one.
    val key = if (canTranspose) viewModel.renderKey(song = song, transposition = transposition, capo = 0, spelling = chordSpelling) else null
    val timeSignature = song.timeSignatureOrDefault.toString()
    val fileName = song.fileName
    return remember(
        viewModel,
        fileName,
        setlistFileName,
        canTranspose,
        shouldShowChords,
        shouldShowTempo,
        transposition,
        key,
        capo,
        tempo,
        timeSignature,
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
            capo = if (shouldShowChords) {
                SongCapoControl(
                    capo = capo,
                    onStep = { viewModel.stepCapo(fileName, setlistFileName, it) },
                    onReset = { viewModel.resetCapo(fileName, setlistFileName) },
                )
            } else {
                null
            },
            tempo = if (shouldShowTempo) {
                SongTempoControl(
                    tempo = tempo,
                    onStep = { viewModel.stepTempo(fileName, setlistFileName, it) },
                    onTapped = { viewModel.setTempo(fileName, setlistFileName, it) },
                    onReset = { viewModel.resetTempo(fileName, setlistFileName) },
                )
            } else {
                null
            },
            timeSignature = timeSignature.takeIf { shouldShowTempo },
        )
    }
}

/**
 * What the header and group edit buttons open: the dialog of each group, on the song details screen's sheet and on the
 * editor preview's card alike. From the editor (a [SongEditTarget.EditorDraft] [target]) they edit the text being typed rather than the file,
 * see [DialogType.SongEdit], and [song] is that text's description of the song, which follows every
 * keystroke, so the latest one is what a button opens its dialog on.
 */
@Composable
internal fun rememberSongInfoEditing(
    viewModel: CampfireViewModel,
    song: Song,
    target: SongEditTarget,
): SongInfoEditing {
    val latestSong by rememberUpdatedState(song)
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val isCoverArtEnabled = userPreferences?.isCoverArtEnabled == true
    return remember(viewModel, target, isCoverArtEnabled) {
        SongInfoEditing(
            onEditCoverArt = if (isCoverArtEnabled) {
                { viewModel.showSongCoverArtDialog(song = latestSong, target = target) }
            } else null,
            onEditMetadata = { viewModel.showSongMetadataDialog(song = latestSong, target = target) },
            onEditTags = { viewModel.showSongTagsDialog(song = latestSong, target = target) },
            onEditLanguages = { viewModel.showSongLanguagesDialog(song = latestSong, target = target) },
            onEditLinks = { viewModel.showSongLinksDialog(song = latestSong, target = target) },
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
 * Opens the "Song defaults" sheet from the song details editing menu or the editor's (the About the song sheet's Song
 * defaults group opens it too): what the file declares for the four values the song is played by, next to what
 * [setlistFileName] (or the library) overrides of them, or what the editor's draft declares for them.
 */
@Composable
internal fun songPlayingAction(
    viewModel: CampfireViewModel,
    song: Song,
    setlistFileName: String?,
    target: SongEditTarget,
) = ActionsMenuItem(
    title = stringResource(Res.string.song_details_playing_edit),
    icon = painterResource(Res.drawable.ic_tune),
    isAlwaysInMenu = true,
    onClick = { viewModel.showSongPlayingDialog(song = song, setlistFileName = setlistFileName, target = target) },
)

/**
 * Opens the cover art sheet from the editor or song details menu.
 */
@Composable
internal fun coverArtAction(
    viewModel: CampfireViewModel,
    song: Song,
    target: SongEditTarget,
) = ActionsMenuItem(
    title = stringResource(if (song.coverArtUrl == null) Res.string.song_details_set_cover_art else Res.string.song_details_change_cover_art),
    icon = painterResource(Res.drawable.ic_album),
    isAlwaysInMenu = true,
    onClick = { viewModel.showSongCoverArtDialog(song = song, target = target) },
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
    target: SongEditTarget,
): List<ActionsMenuItem> = listOf(
    ActionsMenuItem(
        title = stringResource(Res.string.song_details_tags_manage),
        icon = painterResource(Res.drawable.ic_label),
        isAlwaysInMenu = true,
        onClick = { viewModel.showDialog(DialogType.SongTags(song = song, target = target)) },
    ),
    ActionsMenuItem(
        title = stringResource(Res.string.song_details_languages_edit),
        icon = painterResource(Res.drawable.ic_language),
        isAlwaysInMenu = true,
        onClick = { viewModel.showDialog(DialogType.SongLanguages(song = song, target = target)) },
    ),
)
