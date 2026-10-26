/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.firstSection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import com.pandulapeter.campfire.presentation.ui.metronome.timeSignatureOrDefault
import com.pandulapeter.campfire.presentation.ui.playing.SongPlayback
import com.pandulapeter.campfire.presentation.ui.playing.playsWithoutCapo
import com.pandulapeter.campfire.presentation.ui.playing.SEMITONES_PER_OCTAVE
import com.pandulapeter.campfire.presentation.ui.playing.soundingTransposition
import com.pandulapeter.campfire.presentation.ui.playing.transpositionValue
import com.pandulapeter.campfire.presentation.ui.playing.wrapTransposition

/**
 * The controls of the four values a song is played by, for one page of the song details pager: built where that page
 * is drawn, so that each song of a setlist sets its own. The caller leaves them out in read only mode, where the
 * values are read rather than set.
 *
 * @param playback Passed in rather than read here, since the screen already holds it for the page.
 * @param userPreferences The reader's: on the keyboard, which takes no capo (`playsWithoutCapo`), there is no capo
 *   stepper.
 * @param shouldShowChords False with the chords switched off, which takes the transposition and the capo with them.
 * @param shouldShowTempo False with the metronome switched off, which takes the tempo and the time signature with it.
 * @param inferredKey The key the song's chords were found in, see `InferredSongKeys`, for a file that names none.
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
    inferredKey: String?,
): SongPlayingControls? {
    if (!shouldShowChords && !shouldShowTempo) return null
    val (transposition, capo, tempo) = playback
    val canTranspose = shouldShowChords && song.hasChords
    val playsWithoutCapo = userPreferences.playsWithoutCapo
    // The key the page's chords sound in, which the file's capo is counted into and a capo override is not: that keeps
    // the pitch and changes the shapes under it. The stepper itself reads the reader's own semitones, which it steps.
    val fileCapo = capo.songFret
    val key = if (canTranspose) viewModel.songRenderer.renderKey(song = song, transposition = playback.soundingTransposition, spelling = chordSpelling, inferredKey = inferredKey) else null
    val transpositionLabels = remember(viewModel, song, canTranspose, chordSpelling, fileCapo, inferredKey) {
        if (!canTranspose) return@remember emptyList()
        (0 until SEMITONES_PER_OCTAVE).map { semitones ->
            val transposition = wrapTransposition(semitones)
            val shownKey = viewModel.songRenderer.renderKey(song = song, transposition = soundingTransposition(transposition, fileCapo), spelling = chordSpelling, inferredKey = inferredKey)
            transpositionValue(transposition, shownKey)
        }
    }
    // Left out where it is 4/4, as the song lists leave it out: nearly every song is, and it is what the click counts
    // where the file names none.
    val timeSignature = song.timeSignatureOrDefault.takeUnless { it == TimeSignature.COMMON_TIME }?.toString()
    val fileName = song.fileName
    return remember(
        viewModel,
        fileName,
        setlistFileName,
        canTranspose,
        shouldShowChords,
        shouldShowTempo,
        playsWithoutCapo,
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
                    isDefault = transposition == 0,
                    key = key,
                    labels = transpositionLabels,
                    onStep = { viewModel.overrides.stepTransposition(fileName, setlistFileName, it) },
                    onReset = { viewModel.overrides.resetTransposition(fileName, setlistFileName) },
                )
            } else {
                null
            },
            capo = if (shouldShowChords && !playsWithoutCapo) {
                SongCapoControl(
                    capo = capo,
                    onStep = { viewModel.overrides.stepCapo(fileName, setlistFileName, it) },
                    onReset = { viewModel.overrides.resetCapo(fileName, setlistFileName) },
                )
            } else {
                null
            },
            tempo = if (shouldShowTempo) {
                SongTempoControl(
                    tempo = tempo,
                    onStep = { viewModel.overrides.stepTempo(fileName, setlistFileName, it) },
                    onSet = { viewModel.overrides.setTempo(fileName, setlistFileName, it) },
                    onReset = { viewModel.overrides.resetTempo(fileName, setlistFileName) },
                )
            } else {
                null
            },
            timeSignature = timeSignature?.takeIf { shouldShowTempo },
        )
    }
}
