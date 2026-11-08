/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.preferences

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.chords
import com.pandulapeter.campfire.presentation.resources.metronome
import com.pandulapeter.campfire.presentation.resources.preferences_page_song

/**
 * The Preferences' pages as Material's secondary tabs, under Settings' primary ones where the Preferences are a tab of
 * Settings, so that the nesting reads at a glance; the same row in the sheet. Flat like Settings' row: no divider, no
 * container, the page fading into it instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreferencesPageRow(
    modifier: Modifier = Modifier,
    pages: List<PreferencesPage>,
    selectedPage: PreferencesPage,
    onPageSelected: (PreferencesPage) -> Unit,
) = SecondaryTabRow(
    modifier = modifier,
    selectedTabIndex = pages.indexOf(selectedPage).coerceAtLeast(0),
    containerColor = Color.Transparent,
    divider = {},
) {
    pages.forEach { page ->
        Tab(
            selected = page == selectedPage,
            onClick = { onPageSelected(page) },
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            text = { Text(text = page.label(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
    }
}

@Composable
private fun PreferencesPage.label() = stringResource(
    when (this) {
        PreferencesPage.SONG -> Res.string.preferences_page_song
        PreferencesPage.CHORDS -> Res.string.chords
        PreferencesPage.METRONOME -> Res.string.metronome
    },
)
