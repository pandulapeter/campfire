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
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import com.pandulapeter.campfire.presentation.ui.metronome.timeSignatureOrDefault
import com.pandulapeter.campfire.presentation.ui.playing.SongPlayback
import com.pandulapeter.campfire.presentation.ui.playing.readsCapoAsTransposition
import com.pandulapeter.campfire.presentation.ui.playing.shownFor

/**
 * The controls of the four values a song is played by, for one page of the song details pager: built where that page
 * is drawn, so that each song of a setlist sets its own. The caller leaves them out in read only mode, where the
 * values are read rather than set.
 *
 * @param playback Passed in rather than read here, since the screen already holds it for the page.
 * @param userPreferences The reader's, whose capo is read as a transposition on the keyboard, which has no capo: there
 *   is no capo stepper then, and the transposition reads the amount the page's chords are moved by with the capo counted
 *   in, while it still steps and resets only the transposition's own override, see `withCapoSounding`.
 * @param shouldShowChords False with the chords switched off, which takes the transposition and the capo with them.
 * @param shouldShowTempo False with the metronome switched off, which takes the tempo and the time signature with it.
 * @return Null where both are switched off, which leaves the song nothing to set.
 */
@Composable
internal fun rememberSongPlayingControls(
    viewModel: CampfireViewModel,
    song: Song,
    setlistFileName: String?,
    playback: SongPlayback,
    userPreferences: UserPreferences?,
    chordSpelling: UserPreferences.ChordSpelling,
    shouldShowChords: Boolean,
    shouldShowTempo: Boolean,
): SongPlayingControls? {
    if (!shouldShowChords && !shouldShowTempo) return null
    val (transposition, capo, tempo) = playback
    val canTranspose = shouldShowChords && song.hasChords
    val readsCapoAsTransposition = userPreferences.readsCapoAsTransposition
    val shownTransposition = playback.shownFor(userPreferences).transposition
    // The key the page's chords are spelled in: without the capo, which is the stepper next to this one and moves the
    // sounding key without moving a chord, so counting it in here would have this control naming a key that is written
    // nowhere in the song — unless there is no capo stepper, and the page is the sounding key itself.
    val key = if (canTranspose) viewModel.songRenderer.renderKey(song = song, transposition = shownTransposition, capo = 0, spelling = chordSpelling) else null
    val timeSignature = song.timeSignatureOrDefault.toString()
    val fileName = song.fileName
    return remember(
        viewModel,
        fileName,
        setlistFileName,
        canTranspose,
        shouldShowChords,
        shouldShowTempo,
        readsCapoAsTransposition,
        transposition,
        shownTransposition,
        key,
        capo,
        tempo,
        timeSignature,
        song,
    ) {
        SongPlayingControls(
            key = if (canTranspose) {
                SongKeyControl(
                    transposition = shownTransposition,
                    isDefault = transposition == 0,
                    key = key,
                    onStep = { viewModel.stepTransposition(fileName, setlistFileName, it) },
                    onReset = { viewModel.resetTransposition(fileName, setlistFileName) },
                )
            } else {
                null
            },
            capo = if (shouldShowChords && !readsCapoAsTransposition) {
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
