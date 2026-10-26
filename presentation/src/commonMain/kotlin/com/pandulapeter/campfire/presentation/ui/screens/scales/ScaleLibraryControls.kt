/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.scales

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.chrome.TOOL_PAGE_PADDING
import com.pandulapeter.campfire.presentation.ui.components.bleedHorizontally
import com.pandulapeter.campfire.presentation.ui.instrument.ToolCapoControls
import com.pandulapeter.campfire.presentation.ui.instrument.ToolInstrumentSetting
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * What the Scales tab draws its scales on: the instrument in the player's hands and its tuning, through the chips every
 * screen opens their sheets with and written at once as everywhere. The key steps beside the search under them, and the
 * families follow the search ([ScaleFamilyRow]). Right after the pill, the Chords tab's capo ([capo], null where no neck
 * takes one, see `scaleLibraryCapo`), below which the necks are veiled. It reaches past the page's side padding for the reason
 * `ChordLibraryControls` gives, and is spaced as it is.
 */
@Composable
internal fun ScaleLibraryControls(
    modifier: Modifier = Modifier,
    instrument: UserPreferences.Instrument,
    instrumentTuning: InstrumentTuning?,
    notation: ChordNotation,
    onInstrumentClicked: () -> Unit,
    onTuningClicked: () -> Unit,
    capo: Int?,
    onCapoStep: (frets: Int) -> Unit,
    onCapoReset: () -> Unit,
) = ToolInstrumentSetting(
    modifier = modifier.bleedHorizontally(TOOL_PAGE_PADDING),
    instrument = instrument,
    tuning = instrumentTuning,
    notation = notation,
    onInstrumentClicked = onInstrumentClicked,
    onTuningClicked = onTuningClicked,
    isTrailingAfterPill = true,
) {
    ToolCapoControls(
        capo = capo,
        onStep = onCapoStep,
        onReset = onCapoReset,
    )
}
