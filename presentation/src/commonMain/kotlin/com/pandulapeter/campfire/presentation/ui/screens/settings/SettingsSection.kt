/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.LIST_ITEM_KEYLINE
import com.pandulapeter.campfire.presentation.ui.components.SettingsSectionTitle

/**
 * One group of settings. Every one on the settings screen has a [title]: each tab is a column of these, so the pages read
 * alike whichever is open, and the title says what the rows have in common rather than which tab they are on. The
 * Metronome and the Tuner leave theirs untitled, since there the tab names the one group of controls a page has.
 *
 * The rows lie on the screen itself rather than on a card of their own, so a row's ripple and its text keep the
 * keylines of the app bar and of every list in the app.
 */
@Composable
internal fun SettingsSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) = Column(modifier = modifier.fillMaxWidth()) {
    // Close to the first row, which pads itself like a list item: the gap between two stacked sections is what sets
    // the title apart from the section above it.
    title?.let { SettingsSectionTitle(text = it, contentPadding = TITLE_PADDING) }
    content()
}

private val TITLE_PADDING = PaddingValues(start = LIST_ITEM_KEYLINE, end = LIST_ITEM_KEYLINE, top = 8.dp)
