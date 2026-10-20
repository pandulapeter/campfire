/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.instrument

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.components.appBar.LIST_APP_BAR_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.appBar.NEW_ITEM_BUTTON_END_INSET
import com.pandulapeter.campfire.presentation.ui.components.appBar.NEW_ITEM_BUTTON_GAP
import com.pandulapeter.campfire.presentation.ui.songView.songControlHeight
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * The instrument at the top of a tool's page (the Chords, the Scales, and the Tuner tabs), the same on all three: the
 * [InstrumentPill] at the start of a line of the list screens' bar height, with [trailing] at its end where their pill and
 * New button stand (the Tuner's reference pitch). The pill gives up width before it reaches [trailing], cutting its
 * tuning short. A page with nothing at the end keeps the room above the pill all the same, so that the pill stands at one
 * spot whichever tool is picked, but not the room under it, which only parted it from what comes next by more than the
 * page's other groups are parted.
 */
@Composable
internal fun ToolInstrumentSetting(
    modifier: Modifier = Modifier,
    instrument: UserPreferences.Instrument,
    tuning: InstrumentTuning?,
    notation: ChordNotation,
    isEnabled: Boolean = true,
    onInstrumentClicked: () -> Unit,
    onTuningClicked: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) = Row(
    modifier = modifier
        .fillMaxWidth()
        .then(
            if (trailing == null) {
                Modifier.padding(top = (LIST_APP_BAR_HEIGHT - songControlHeight(MaterialTheme.typography.titleSmall)) / 2)
            } else {
                Modifier.height(LIST_APP_BAR_HEIGHT)
            },
        )
        .padding(start = 16.dp, end = NEW_ITEM_BUTTON_END_INSET),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    InstrumentPill(
        modifier = Modifier.weight(1f, fill = false).padding(end = NEW_ITEM_BUTTON_GAP),
        instrument = instrument,
        tuning = tuning,
        notation = notation,
        isEnabled = isEnabled,
        onInstrumentClicked = onInstrumentClicked,
        onTuningClicked = onTuningClicked,
    )
    trailing?.invoke()
}
