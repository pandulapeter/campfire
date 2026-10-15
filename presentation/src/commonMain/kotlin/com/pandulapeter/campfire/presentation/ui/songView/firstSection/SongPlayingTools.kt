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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.metronome.api.model.MetronomeBeat
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The tools a song is played with, as buttons of the song's own first section rather than of the app bar: the tuner next
 * to the instrument and tuning chips where they are shown, else the transposition and the capo, which say what the
 * strings are tuned for, and the metronome next to the tempo and
 * the time signature, which say what it plays. The bar is left with what is about the song itself. They are there in
 * read only mode too, after the line of text the values are read in there, since neither changes anything about a song.
 *
 * Where the values a button belongs next to are not on the page, it goes next to the others, and alone where neither is
 * ([playingToolPlaces]). The section is there wherever the chords or the metronome are switched on, so only with both
 * off is there nowhere for the tuner, which the menu then holds.
 */
@Immutable
internal class SongPlayingTools(
    /** Null with the tuner switched off. */
    val onTunerClicked: (() -> Unit)?,
    /** Whether the tuner sheet is open over the song, which draws its button as switched on, as the metronome's is. */
    val isTunerSheetShown: Boolean,
    /** Null with the metronome switched off. */
    val metronome: SongMetronomeTool?,
)

/** What the metronome button of [SongPlayingTools] draws, see `MetronomeButton`. */
@Immutable
internal class SongMetronomeTool(
    val isPanelShown: Boolean,
    val bpm: Int,
    val isFlashEnabled: Boolean,
    val playback: StateFlow<MetronomePlayback>,
    val beats: Flow<MetronomeBeat>,
    val onClick: () -> Unit,
)

/**
 * The tools of one page of the song details pager, [bpm] being the tempo a click started from that page would play at.
 *
 * @return Null where both are switched off.
 */
@Composable
internal fun rememberSongPlayingTools(
    viewModel: CampfireViewModel,
    bpm: Int,
    isMetronomeEnabled: Boolean,
    isTunerEnabled: Boolean,
    isTunerSheetShown: Boolean,
    isMetronomePanelShown: Boolean,
    isBeatFlashEnabled: Boolean,
): SongPlayingTools? = remember(viewModel, bpm, isMetronomeEnabled, isTunerEnabled, isTunerSheetShown, isMetronomePanelShown, isBeatFlashEnabled) {
    if (!isMetronomeEnabled && !isTunerEnabled) {
        null
    } else {
        SongPlayingTools(
            onTunerClicked = if (isTunerEnabled) viewModel::openTunerSheet else null,
            isTunerSheetShown = isTunerSheetShown,
            metronome = if (isMetronomeEnabled) {
                SongMetronomeTool(
                    isPanelShown = isMetronomePanelShown,
                    bpm = bpm,
                    isFlashEnabled = isBeatFlashEnabled,
                    playback = viewModel.metronomeController.metronomePlayback,
                    beats = viewModel.metronomeController.metronomeBeats,
                    onClick = viewModel.metronomeController::toggleMetronomePanel,
                )
            } else {
                null
            },
        )
    }
}

/** Which of the groups of the playing values each of the [SongPlayingTools] follows, see [playingToolPlaces]. */
internal enum class PlayingToolPlace { AFTER_INSTRUMENT_CHIPS, AFTER_CHORD_VALUES, AFTER_CLICK_VALUES, ALONE }

/** Where the tuner and the metronome buttons go, each null where that tool is switched off. */
internal data class PlayingToolPlaces(
    val tuner: PlayingToolPlace?,
    val metronome: PlayingToolPlace?,
)

/**
 * Where the buttons of [SongPlayingTools] stand among the playing values: the tuner after the instrument and tuning chips
 * ([hasInstrumentChips]), which name what it tunes, else after the key and the capo ([hasChordValues]), the metronome after the tempo and the time signature ([hasClickValues]). A song shown without the
 * values a button belongs next to — the chords switched off, or a song on the keyboard with no chords to transpose —
 * has it after the other group instead, and one shown with neither has the buttons on their own.
 */
internal fun playingToolPlaces(
    hasTuner: Boolean,
    hasMetronome: Boolean,
    hasInstrumentChips: Boolean,
    hasChordValues: Boolean,
    hasClickValues: Boolean,
): PlayingToolPlaces {
    fun placeOf(home: PlayingToolPlace, isHomeShown: Boolean, isOtherShown: Boolean) = when {
        isHomeShown -> home
        isOtherShown -> if (home == PlayingToolPlace.AFTER_CHORD_VALUES) PlayingToolPlace.AFTER_CLICK_VALUES else PlayingToolPlace.AFTER_CHORD_VALUES
        else -> PlayingToolPlace.ALONE
    }
    return PlayingToolPlaces(
        tuner = when {
            !hasTuner -> null
            hasInstrumentChips -> PlayingToolPlace.AFTER_INSTRUMENT_CHIPS
            else -> placeOf(PlayingToolPlace.AFTER_CHORD_VALUES, hasChordValues, hasClickValues)
        },
        metronome = if (hasMetronome) placeOf(PlayingToolPlace.AFTER_CLICK_VALUES, hasClickValues, hasChordValues) else null,
    )
}

/** Where [playingToolPlaces] put the buttons of these tools, the playing values being as said. */
internal fun SongPlayingTools.places(hasInstrumentChips: Boolean, hasChordValues: Boolean, hasClickValues: Boolean) = playingToolPlaces(
    hasTuner = onTunerClicked != null,
    hasMetronome = metronome != null,
    hasInstrumentChips = hasInstrumentChips,
    hasChordValues = hasChordValues,
    hasClickValues = hasClickValues,
)
