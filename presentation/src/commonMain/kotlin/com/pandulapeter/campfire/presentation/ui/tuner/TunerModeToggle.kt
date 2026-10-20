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
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.TunerSettings
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner_mode_chromatic
import com.pandulapeter.campfire.presentation.resources.tuner_strings

/**
 * Whether the tuner reads against the instrument's strings or every note: a small segmented row, on the tab under the
 * reference pitch's play circle and in the sheet over a song in its header, small enough not to read as part of what is heard. Left
 * out by both for an instrument with no strings to tune (the keyboard, Other), which is read note by note either way.
 * Its segments carry no check mark, whose room is most of what a label has at this size; the fill marks the choice.
 */
@Composable
internal fun TunerModeToggle(
    modifier: Modifier = Modifier,
    mode: TunerSettings.Mode,
    onModeSelected: (TunerSettings.Mode) -> Unit,
) = SingleChoiceSegmentedButtonRow(modifier = modifier) {
    val options = listOf(
        TunerSettings.Mode.STRINGS to stringResource(Res.string.tuner_strings),
        TunerSettings.Mode.CHROMATIC to stringResource(Res.string.tuner_mode_chromatic),
    )
    options.forEachIndexed { index, (value, label) ->
        SegmentedButton(
            // Set below Material's 40dp minimum, which a modifier's own height takes precedence over.
            modifier = Modifier.height(TUNER_MODE_TOGGLE_HEIGHT),
            selected = value == mode,
            onClick = { onModeSelected(value) },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            icon = {},
            contentPadding = TUNER_MODE_TOGGLE_PADDING,
            label = {
                Text(
                    text = label,
                    // Named rather than inherited: the sheet's header sets its actions' labels at the title's size.
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            },
        )
    }
}

/** Also the room the Tuner tab keeps for the toggle where it is hidden, so that the display under it stays put. */
internal val TUNER_MODE_TOGGLE_HEIGHT = 32.dp
private val TUNER_MODE_TOGGLE_PADDING = PaddingValues(horizontal = 12.dp)
