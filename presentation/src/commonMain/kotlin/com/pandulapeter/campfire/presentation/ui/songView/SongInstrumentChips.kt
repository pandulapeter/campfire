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
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.pandulapeter.campfire.presentation.ui.components.InstrumentChips
import com.pandulapeter.campfire.presentation.ui.components.controls.PICKER_CHIP_START_PADDING

/**
 * The instrument and tuning chips as a song's page draws them: as tall as a section header's pill and set in its style,
 * their chevrons scaled with the lyrics, opening the instrument's sheet and the tuning's ([ChordDiagrams.onInstrumentClicked],
 * [ChordDiagrams.onTuningClicked]). Each reaches out of its column to the left as a section's pill does, so that a chip
 * starting a line has its name on the keyline the lyrics and the section titles stand on. Emits the chips without a
 * container, for the flow that heads the song's first section. The tuning chip fades in and out by [tuningChipVisibility],
 * which [SongLyrics] reads to know when a leaving chip is no longer part of the section it measures.
 */
@Composable
internal fun SongInstrumentChips(
    chordDiagrams: ChordDiagrams,
    headerStyle: TextStyle,
    fontScale: Float,
    tuningChipVisibility: MutableTransitionState<Boolean>,
    onInstrumentClicked: () -> Unit,
    onTuningClicked: () -> Unit,
) = InstrumentChips(
    chipModifier = Modifier.offset(x = -PICKER_CHIP_START_PADDING),
    instrument = chordDiagrams.instrument,
    tuning = chordDiagrams.instrumentTuning,
    notation = chordDiagrams.notation,
    height = songControlHeight(headerStyle),
    style = headerStyle,
    chevronSize = FOLD_CHEVRON_SIZE * fontScale,
    tuningChipVisibility = tuningChipVisibility,
    onInstrumentClicked = onInstrumentClicked,
    onTuningClicked = onTuningClicked,
)
