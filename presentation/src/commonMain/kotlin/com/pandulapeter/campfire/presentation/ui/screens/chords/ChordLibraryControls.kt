/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.chords

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.chrome.TOOL_PAGE_PADDING
import com.pandulapeter.campfire.presentation.ui.components.bleedHorizontally
import com.pandulapeter.campfire.presentation.ui.instrument.ToolInstrumentSetting
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * What the Chords tab lists the chords for: the instrument in the player's hands and its tuning, through the chips every
 * screen opens their sheets with; the key steps before the search under them (`pinnedSearchField`), which stays on
 * screen. The instrument is the app's one, which the songs' diagrams are drawn for too, so a pick here is written at
 * once and changes them as well.
 *
 * It is the tools' shared [ToolInstrumentSetting], the same on the Scales and the Tuner tabs. It stands in the page's
 * grid, inside the page's side padding, while the row pads itself as a sheet's does; so it reaches past that padding by as much ([TOOL_PAGE_PADDING]) and lines its own rows up with it.
 */
@Composable
internal fun ChordLibraryControls(
    modifier: Modifier = Modifier,
    instrument: UserPreferences.Instrument,
    instrumentTuning: InstrumentTuning?,
    notation: ChordNotation,
    onInstrumentClicked: () -> Unit,
    onTuningClicked: () -> Unit,
) = ToolInstrumentSetting(
    // Nothing under it: the search's own padding and the grid's spacing are the gap to the line under it (TOOL_GROUP_GAP).
    modifier = modifier.bleedHorizontally(TOOL_PAGE_PADDING),
    instrument = instrument,
    tuning = instrumentTuning,
    notation = notation,
    onInstrumentClicked = onInstrumentClicked,
    onTuningClicked = onTuningClicked,
)
