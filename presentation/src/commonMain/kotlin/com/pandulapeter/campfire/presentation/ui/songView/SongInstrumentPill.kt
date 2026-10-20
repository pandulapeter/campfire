/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import com.pandulapeter.campfire.presentation.ui.instrument.InstrumentPill

/**
 * The instrument and tuning pill as a song's page draws it: set in a section header's style, and so exactly as tall as
 * the transposition stepper and the other playing controls under it, opening the instrument's sheet and the tuning's
 * ([ChordDiagrams.onInstrumentClicked], [ChordDiagrams.onTuningClicked]). The row it is put in starts it as far short of the
 * keyline the lyrics and the section titles stand on as a section's pill reaches, so that their edges line up. The tuning half fades in and out by [tuningVisibility], which [SongLyrics] reads to know when a leaving half is
 * no longer part of the section it measures.
 */
@Composable
internal fun SongInstrumentPill(
    chordDiagrams: ChordDiagrams,
    headerStyle: TextStyle,
    tuningVisibility: MutableTransitionState<Boolean>,
    onInstrumentClicked: () -> Unit,
    onTuningClicked: () -> Unit,
) = InstrumentPill(
    instrument = chordDiagrams.instrument,
    tuning = chordDiagrams.instrumentTuning,
    notation = chordDiagrams.notation,
    style = headerStyle,
    tuningVisibility = tuningVisibility,
    onInstrumentClicked = onInstrumentClicked,
    onTuningClicked = onTuningClicked,
)
