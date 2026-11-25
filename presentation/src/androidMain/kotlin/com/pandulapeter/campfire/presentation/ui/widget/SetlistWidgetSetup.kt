/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.widget

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * The setlist widget's setup screen: Next gig, then every setlist of [snapshot] by its title, the one [initialFileName]
 * names (null for Next gig) selected at first. Done calls [onDone] with the selected one's file name, or null for Next
 * gig.
 */
@Composable
fun SetlistWidgetSetup(
    snapshot: WidgetSnapshot,
    initialFileName: String?,
    onDone: (String?) -> Unit,
) {
    var selectedFileName by rememberSaveable { mutableStateOf<String?>(initialFileName) }
    WidgetSetupScaffold(
        snapshot = snapshot,
        onDone = { onDone(selectedFileName) },
    ) {
        item(key = NEXT_GIG_KEY) {
            Choice(
                title = snapshot.strings.nextGig,
                description = snapshot.strings.nextGigDescription,
                isSelected = selectedFileName == null,
                onClick = { selectedFileName = null },
            )
        }
        items(items = snapshot.setlists, key = { it.fileName }) { setlist ->
            Choice(
                title = setlist.title,
                description = setlist.formattedDate,
                isSelected = setlist.fileName == selectedFileName,
                onClick = { selectedFileName = setlist.fileName },
            )
        }
    }
}

@Composable
private fun Choice(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) = ListItem(
    modifier = Modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
    headlineContent = { Text(text = title) },
    supportingContent = description.takeIf { it.isNotBlank() }?.let { { Text(text = it) } },
    leadingContent = { RadioButton(selected = isSelected, onClick = null) },
    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
)

/** The list's own row's key, which no setlist's file name (always `.setlist.json`) can be. */
private const val NEXT_GIG_KEY = "next_gig"
