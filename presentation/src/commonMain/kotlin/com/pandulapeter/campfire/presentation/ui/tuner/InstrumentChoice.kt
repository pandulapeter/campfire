/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_expand
import com.pandulapeter.campfire.presentation.resources.tuner_instrument
import com.pandulapeter.campfire.presentation.ui.components.LabeledControlRow
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import org.jetbrains.compose.resources.painterResource

/**
 * What a sound is read against, on the Tuner tab alone: a button naming the instrument and its tuning at the end of its
 * row, opening the sheet it is picked in ([InstrumentPicker]), since some thirty tunings across five families are more
 * than a page of settings should hold for a choice made once per instrument picked up. The sheet over a song tunes to
 * whatever is picked here.
 */
@Composable
internal fun InstrumentChoice(
    selected: InstrumentTuning?,
    onClick: () -> Unit,
) = LabeledControlRow(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    label = {
        Text(
            text = stringResource(Res.string.tuner_instrument),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    },
) {
    OutlinedButton(
        onClick = onClick,
        // Material's padding for a button with an icon is for a leading one; the chevron trails the name here.
        contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
    ) {
        Text(text = instrumentLabel(selected))
        Icon(
            modifier = Modifier.padding(start = 4.dp).size(ButtonDefaults.IconSize),
            painter = painterResource(Res.drawable.ic_expand),
            contentDescription = null,
        )
    }
}
