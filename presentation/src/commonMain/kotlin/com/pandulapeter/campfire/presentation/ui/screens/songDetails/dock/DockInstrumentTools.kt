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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.pandulapeter.campfire.presentation.ui.instrument.tunings
import com.pandulapeter.campfire.presentation.ui.songView.SongInstrumentPill
import com.pandulapeter.campfire.presentation.ui.songView.songControlHeight
import com.pandulapeter.campfire.presentation.ui.tuner.TunerButton

/**
 * What starts the dock where the key chip opens no Playing sheet to hold it ([keyOpensPlaying] false: read only mode, or
 * the chords switched off): the instrument and tuning pill where the diagrams are on, and the tuner. An instrument
 * without shapes keeps the pill here even beside a chip that opens the sheet, since it is what brings the diagrams back.
 * Emits nothing where neither is there.
 *
 * In the side panel ([pillWidth], its controls' width) both are that wide and [pillHeight] tall, a column of controls
 * with the key chip over them: the tuner first, as the pill naming the tuning that stands under the key chip elsewhere
 * ([DockTunerPill]) rather than the round button, then the instrument pill.
 */
@Composable
internal fun DockInstrumentTools(
    content: DockContent,
    keyOpensPlaying: Boolean,
    pillWidth: Dp?,
    pillHeight: Dp,
) {
    val chordDiagrams = content.chordDiagrams
    val onInstrumentClicked = chordDiagrams?.onInstrumentClicked
    val instrumentPill = if (chordDiagrams != null && onInstrumentClicked != null && content.showsInstrumentPill(keyOpensPlaying)) {
        @Composable {
            val tuningVisibility = remember { MutableTransitionState(chordDiagrams.instrument.tunings.size > 1) }
                .apply { targetState = chordDiagrams.instrument.tunings.size > 1 }
            SongInstrumentPill(
                modifier = if (pillWidth == null) Modifier else Modifier.width(pillWidth),
                chordDiagrams = chordDiagrams,
                headerStyle = MaterialTheme.typography.titleSmall,
                tuningVisibility = tuningVisibility,
                onInstrumentClicked = onInstrumentClicked,
                onTuningClicked = chordDiagrams.onTuningClicked ?: {},
            )
        }
    } else {
        null
    }
    val tools = content.playingTools
    val onTunerClicked = tools?.onTunerClicked
    val tuner = if (onTunerClicked != null && content.showsTuner(keyOpensPlaying)) {
        @Composable {
            if (pillWidth != null && chordDiagrams != null) {
                DockTunerPill(modifier = Modifier.width(pillWidth), chordDiagrams = chordDiagrams, height = pillHeight, onClick = onTunerClicked)
            } else {
                TunerButton(
                    isSheetShown = tools.isTunerSheetShown,
                    height = songControlHeight(MaterialTheme.typography.titleSmall),
                    fontScale = 1f,
                    onClick = onTunerClicked,
                )
            }
        }
    } else {
        null
    }
    if (pillWidth == null) {
        instrumentPill?.invoke()
        tuner?.invoke()
    } else {
        tuner?.invoke()
        instrumentPill?.invoke()
    }
}

/** Whether [DockInstrumentTools] draws anything for this content beside a key chip that does or does not open the Playing sheet. */
internal fun DockContent.hasInstrumentTools(keyOpensPlaying: Boolean) = showsInstrumentPill(keyOpensPlaying) || showsTuner(keyOpensPlaying)

/** Whether [DockInstrumentTools] draws the instrument pill beside a key chip that does or does not open the Playing sheet. */
internal fun DockContent.showsInstrumentPill(keyOpensPlaying: Boolean) =
    chordDiagrams?.onInstrumentClicked != null && (!keyOpensPlaying || chordDiagrams.tuning == null)

/** Whether [DockInstrumentTools] draws the tuner button beside a key chip that does or does not open the Playing sheet. */
internal fun DockContent.showsTuner(keyOpensPlaying: Boolean) = playingTools?.onTunerClicked != null && !keyOpensPlaying
