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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.theme.CampfireColorScheme
import com.pandulapeter.campfire.presentation.ui.theme.dynamicColorSchemePair
import com.pandulapeter.campfire.presentation.ui.theme.interfaceTypography

/**
 * The setlist widget's configuration screen, which `:app:android`'s configuration activity shows as a widget is added or
 * reconfigured: Next gig, then every setlist of [snapshot] by its title, the one [chosenFileName] names (null for Next
 * gig) selected. Choosing one calls [onChosen] with its file name, or null for Next gig.
 *
 * Drawn in the colors the widget itself takes (the wallpaper's where the system has them, the app's own otherwise) and
 * in the system's dark mode, since the app's preferences are not read here, but in the app's typeface and in the
 * snapshot's language and direction, as the widget is.
 */
@Composable
fun SetlistWidgetChooser(
    snapshot: WidgetSnapshot,
    chosenFileName: String?,
    onChosen: (String?) -> Unit,
) {
    val colorSchemePair = dynamicColorSchemePair() ?: CampfireColorScheme
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) colorSchemePair.dark else colorSchemePair.light,
        typography = interfaceTypography() ?: MaterialTheme.typography,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides if (snapshot.strings.isRightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                LazyColumn(contentPadding = WindowInsets.safeDrawing.asPaddingValues()) {
                    item(key = TITLE_KEY) {
                        Text(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                            text = snapshot.strings.chooseSetlist,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                    item(key = NEXT_GIG_KEY) {
                        Choice(
                            title = snapshot.strings.nextGig,
                            description = snapshot.strings.nextGigDescription,
                            isSelected = chosenFileName == null,
                            onClick = { onChosen(null) },
                        )
                    }
                    items(items = snapshot.setlists, key = { it.fileName }) { setlist ->
                        Choice(
                            title = setlist.title,
                            description = setlist.formattedDate,
                            isSelected = setlist.fileName == chosenFileName,
                            onClick = { onChosen(setlist.fileName) },
                        )
                    }
                }
            }
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
    modifier = Modifier.clickable(onClick = onClick),
    headlineContent = { Text(text = title) },
    supportingContent = description.takeIf { it.isNotBlank() }?.let { { Text(text = it) } },
    leadingContent = { RadioButton(selected = isSelected, onClick = null) },
    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
)

/** The list's own rows' keys, which no setlist's file name (always `.setlist.json`) can be. */
private const val TITLE_KEY = "title"
private const val NEXT_GIG_KEY = "next_gig"
