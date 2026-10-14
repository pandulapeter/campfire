/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.importReport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.ImportConflictResolution
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_check
import com.pandulapeter.campfire.presentation.resources.ic_expand
import com.pandulapeter.campfire.presentation.resources.import_conflicts_keep_both
import com.pandulapeter.campfire.presentation.resources.import_conflicts_keep_both_description
import com.pandulapeter.campfire.presentation.resources.import_conflicts_mixed
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace_description
import com.pandulapeter.campfire.presentation.resources.import_review_show_library_song
import com.pandulapeter.campfire.presentation.ui.components.menu.OverflowMenu
import org.jetbrains.compose.resources.painterResource

/**
 * What a name already taken does, as an outlined button reading the current choice that opens a popup of the two, each
 * with its one line of description; the same for one row and, in the header of the group, for all of them.
 *
 * @param resolution Null where the rows of the group differ, which the button reads as Mixed and no item is checked for.
 * @param onShowLibrarySong Where the row is about a song the library still holds, opens that song, so the one being
 *   replaced can be read before the choice is made; null for the group's header and for a setlist.
 */
@Composable
internal fun ConflictResolutionDropdown(
    modifier: Modifier = Modifier,
    resolution: ImportConflictResolution?,
    onResolutionSelected: (ImportConflictResolution) -> Unit,
    onShowLibrarySong: (() -> Unit)? = null,
) = OverflowMenu(
    modifier = modifier,
    button = { open ->
        OutlinedButton(
            onClick = open,
            contentPadding = PaddingValues(start = 12.dp, end = 8.dp),
        ) {
            Text(text = stringResource(resolution?.label ?: Res.string.import_conflicts_mixed), maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Icon(modifier = Modifier.size(18.dp), painter = painterResource(Res.drawable.ic_expand), contentDescription = null)
        }
    },
) { select ->
    ImportConflictResolution.entries.forEach { option ->
        DropdownMenuItem(
            text = {
                // A menu item is as tight as a one-line one, and these descriptions run to several lines.
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(stringResource(option.label))
                    Text(
                        text = stringResource(option.description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingIcon = {
                // Every entry keeps the room of the mark, so that the labels line up whichever one is chosen.
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(Res.drawable.ic_check),
                    contentDescription = null,
                    tint = if (option == resolution) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                )
            },
            onClick = { select { onResolutionSelected(option) } },
        )
    }
    if (onShowLibrarySong != null) {
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.import_review_show_library_song)) },
            onClick = { select(onShowLibrarySong) },
        )
    }
}

private val ImportConflictResolution.label
    get() = when (this) {
        ImportConflictResolution.KEEP_BOTH -> Res.string.import_conflicts_keep_both
        ImportConflictResolution.REPLACE -> Res.string.import_conflicts_replace
    }

private val ImportConflictResolution.description
    get() = when (this) {
        ImportConflictResolution.KEEP_BOTH -> Res.string.import_conflicts_keep_both_description
        ImportConflictResolution.REPLACE -> Res.string.import_conflicts_replace_description
    }
