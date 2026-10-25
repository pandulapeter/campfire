/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor.toolbar

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import com.pandulapeter.campfire.presentation.ui.components.menu.OverflowMenu

/**
 * A button of the [EditorToolbar] that asks which of several things to write before it writes any of them, as a popup
 * hanging from it: the Tab and Chord shape buttons, whose choices are the instruments a tab or a chord shape can be
 * written for, and More details and Comment, which keep what is reached for less often out of the rows.
 *
 * The popup takes no focus, and neither do its entries, for the reason the buttons take none: the caret has to stay in
 * the field for the keyboard to stay up and for the choice to be written where it is.
 *
 * @param choices The label of each entry, in the order they are listed.
 * @param onChoiceSelected Called with the index of the entry picked in [choices], once the menu is closed.
 */
@Composable
internal fun EditorToolbarMenuButton(
    modifier: Modifier = Modifier,
    label: String,
    choices: List<String>,
    onChoiceSelected: (Int) -> Unit,
) = OverflowMenu(
    modifier = modifier,
    isFocusable = false,
    button = { open ->
        EditorToolbarButton(
            label = label,
            hasMenu = true,
            onClick = open,
        )
    },
) { select ->
    choices.forEachIndexed { index, choice ->
        DropdownMenuItem(
            modifier = Modifier.focusProperties { canFocus = false },
            text = { Text(choice) },
            onClick = { select { onChoiceSelected(index) } },
        )
    }
}
