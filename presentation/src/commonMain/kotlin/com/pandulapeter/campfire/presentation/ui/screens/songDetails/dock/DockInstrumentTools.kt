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
 * What follows the key chip at the dock's start (or starts it where there is none): the tuner, then the instrument and
 * tuning pill. The pill is there where the diagrams are on and the key chip opens no Playing sheet to hold it
 * ([keyOpensPlaying] false: read only mode, or the chords switched off), and the tuner wherever the key chip does not
 * stand over its pill ([keyHoldsTuner], the diagram row's and the side panel's [DockKeyAndTuner]), so that the tuner is
 * never more than one press away, the Playing sheet having none. An instrument without shapes keeps the pill here even
 * beside a chip that opens the sheet, since it is what brings the diagrams back. Emits nothing where neither is there.
 *
 * The tuner comes first, closest to the key chip, as its pill stands under the key chip in the diagram row, and the
 * instrument pill, the widest, last, where it ends the start before what scrolls. In the side panel ([pillWidth], its
 * controls' width) both are that wide and [pillHeight] tall, a column of controls under the key chip, the tuner as the
 * pill naming the tuning ([DockTunerPill]) rather than the round button.
 */
@Composable
internal fun DockInstrumentTools(
    content: DockContent,
    keyOpensPlaying: Boolean,
    keyHoldsTuner: Boolean,
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
    val tuner = if (onTunerClicked != null && content.showsTuner(keyHoldsTuner)) {
        @Composable {
            if (pillWidth != null && chordDiagrams != null) {
                DockTunerPill(modifier = Modifier.width(pillWidth), chordDiagrams = chordDiagrams, height = pillHeight, onClick = onTunerClicked)
            } else {
                TunerButton(
                    isSheetShown = tools.isTunerSheetShown,
                    height = songControlHeight(MaterialTheme.typography.titleSmall),
                    fontScale = 1f,
                    onClick = {
                        println("Dock: tuner button")
                        onTunerClicked()
                    },
                )
            }
        }
    } else {
        null
    }
    tuner?.invoke()
    instrumentPill?.invoke()
}

/**
 * Whether [DockInstrumentTools] draws anything for this content beside a key chip that does or does not open the Playing
 * sheet, and does or does not stand over the tuner's pill.
 */
internal fun DockContent.hasInstrumentTools(keyOpensPlaying: Boolean, keyHoldsTuner: Boolean) =
    showsInstrumentPill(keyOpensPlaying) || showsTuner(keyHoldsTuner)

/** Whether [DockInstrumentTools] draws the instrument pill beside a key chip that does or does not open the Playing sheet. */
internal fun DockContent.showsInstrumentPill(keyOpensPlaying: Boolean) =
    chordDiagrams?.onInstrumentClicked != null && (!keyOpensPlaying || chordDiagrams.tuning == null)

/** Whether [DockInstrumentTools] draws the tuner beside a key chip that does or does not stand over the tuner's pill. */
internal fun DockContent.showsTuner(keyHoldsTuner: Boolean) = playingTools?.onTunerClicked != null && !keyHoldsTuner

/**
 * Whether a key chip that does or does not open the Playing sheet ([keyOpensPlaying]) stands over the tuner's pill
 * ([DockKeyAndTuner]): only where it is as tall as the diagram row ([isTall], the side panel's included) beside diagrams.
 */
internal fun DockContent.keyHoldsTuner(keyOpensPlaying: Boolean, isTall: Boolean) =
    isTall && keyOpensPlaying && playingTools?.onTunerClicked != null && chordDiagrams != null
