/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.importing.ImportConflictResolution
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_tune
import com.pandulapeter.campfire.presentation.resources.import_conflicts_keep_both
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace
import com.pandulapeter.campfire.presentation.resources.import_preview_choices
import com.pandulapeter.campfire.presentation.resources.import_preview_include
import com.pandulapeter.campfire.presentation.ui.components.menu.OverflowMenu
import org.jetbrains.compose.resources.painterResource

/**
 * The page's choices over the review in the compact preview card of a short window, behind a button of their own: the
 * tick, and for a taken name Keep both and Replace. Every entry acts and leaves the menu open, so that what it did is
 * read on the card's line beside the button.
 *
 * @param resolution What the page's taken name does, null where its name is not taken.
 */
@Composable
internal fun ImportPreviewChoicesMenu(
    modifier: Modifier = Modifier,
    isChosen: Boolean,
    resolution: ImportConflictResolution?,
    onChosenChange: (Boolean) -> Unit,
    onResolutionSelected: (ImportConflictResolution) -> Unit,
) = OverflowMenu(
    modifier = modifier,
    button = { open ->
        IconButton(onClick = open) {
            Icon(painter = painterResource(Res.drawable.ic_tune), contentDescription = stringResource(Res.string.import_preview_choices))
        }
    },
) {
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.import_preview_include)) },
        leadingIcon = { Checkbox(checked = isChosen, onCheckedChange = null) },
        onClick = { onChosenChange(!isChosen) },
    )
    if (resolution != null) {
        listOf(
            ImportConflictResolution.KEEP_BOTH to Res.string.import_conflicts_keep_both,
            ImportConflictResolution.REPLACE to Res.string.import_conflicts_replace,
        ).forEach { (option, label) ->
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                leadingIcon = { RadioButton(selected = option == resolution, onClick = null) },
                onClick = { onResolutionSelected(option) },
            )
        }
    }
}
