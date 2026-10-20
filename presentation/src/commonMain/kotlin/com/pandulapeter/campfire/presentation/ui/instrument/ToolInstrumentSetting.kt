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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.instrument
import com.pandulapeter.campfire.presentation.ui.components.appBar.LIST_APP_BAR_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.appBar.NEW_ITEM_BUTTON_END_INSET
import com.pandulapeter.campfire.presentation.ui.components.appBar.NEW_ITEM_BUTTON_GAP
import com.pandulapeter.campfire.presentation.ui.components.appBar.NEW_ITEM_BUTTON_TOP_INSET
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * The instrument at the top of a tool's page (the Chords, the Scales, and the Tuner tabs), the same on all three: its
 * title in a line of the list screens' bar height, with [trailing] at its end where their pill and New button stand
 * (the Tuner's reference pitch), and the [InstrumentPill] under that line, so that the corner's circle never covers it.
 * A page with nothing in the corner keeps the line all the same, so that the pill stands at one height whichever tool is
 * picked.
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
) = Column(modifier = modifier) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LIST_APP_BAR_HEIGHT)
            .padding(start = 16.dp, end = NEW_ITEM_BUTTON_END_INSET),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Set as a settings row's title is.
        Text(
            modifier = Modifier.weight(1f).padding(end = NEW_ITEM_BUTTON_GAP),
            text = stringResource(Res.string.instrument),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
    InstrumentPill(
        // The New button's circle is as far from the line's bottom as from its top; the pill leaves as much again.
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = NEW_ITEM_BUTTON_TOP_INSET),
        instrument = instrument,
        tuning = tuning,
        notation = notation,
        isEnabled = isEnabled,
        onInstrumentClicked = onInstrumentClicked,
        onTuningClicked = onTuningClicked,
    )
}
