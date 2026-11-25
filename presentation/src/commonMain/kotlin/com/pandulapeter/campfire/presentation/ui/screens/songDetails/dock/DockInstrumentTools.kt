/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.presentation.ui.instrument.tunings
import com.pandulapeter.campfire.presentation.ui.songView.SongInstrumentPill
import com.pandulapeter.campfire.presentation.ui.songView.songControlHeight
import com.pandulapeter.campfire.presentation.ui.tuner.TunerButton

/**
 * What starts the dock where the key chip opens no Playing sheet to hold it ([keyOpensPlaying] false: read only mode, or
 * the chords switched off): the instrument and tuning pill where the diagrams are on, and the tuner. An instrument
 * without shapes keeps the pill here even beside a chip that opens the sheet, since it is what brings the diagrams back.
 * Emits nothing where neither is there.
 */
@Composable
internal fun DockInstrumentTools(
    content: DockContent,
    keyOpensPlaying: Boolean,
) {
    val chordDiagrams = content.chordDiagrams
    val onInstrumentClicked = chordDiagrams?.onInstrumentClicked
    if (chordDiagrams != null && onInstrumentClicked != null && content.showsInstrumentPill(keyOpensPlaying)) {
        val tuningVisibility = remember { MutableTransitionState(chordDiagrams.instrument.tunings.size > 1) }
            .apply { targetState = chordDiagrams.instrument.tunings.size > 1 }
        SongInstrumentPill(
            chordDiagrams = chordDiagrams,
            headerStyle = MaterialTheme.typography.titleSmall,
            tuningVisibility = tuningVisibility,
            onInstrumentClicked = onInstrumentClicked,
            onTuningClicked = chordDiagrams.onTuningClicked ?: {},
        )
    }
    val tools = content.playingTools
    val onTunerClicked = tools?.onTunerClicked
    if (onTunerClicked != null && content.showsTuner(keyOpensPlaying)) {
        TunerButton(
            isSheetShown = tools.isTunerSheetShown,
            height = songControlHeight(MaterialTheme.typography.titleSmall),
            fontScale = 1f,
            onClick = onTunerClicked,
        )
    }
}

/** Whether [DockInstrumentTools] draws anything for this content beside a key chip that does or does not open the Playing sheet. */
internal fun DockContent.hasInstrumentTools(keyOpensPlaying: Boolean) = showsInstrumentPill(keyOpensPlaying) || showsTuner(keyOpensPlaying)

/** Whether [DockInstrumentTools] draws the instrument pill beside a key chip that does or does not open the Playing sheet. */
internal fun DockContent.showsInstrumentPill(keyOpensPlaying: Boolean) =
    chordDiagrams?.onInstrumentClicked != null && (!keyOpensPlaying || chordDiagrams.tuning == null)

/** Whether [DockInstrumentTools] draws the tuner button beside a key chip that does or does not open the Playing sheet. */
internal fun DockContent.showsTuner(keyOpensPlaying: Boolean) = playingTools?.onTunerClicked != null && !keyOpensPlaying
