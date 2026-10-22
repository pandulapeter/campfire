/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.scales

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.pandulapeter.campfire.presentation.ui.components.controls.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.components.controls.SelectableChip

/**
 * The Scales tab's families as one line of chips scrolling sideways, a radio group, under the search. Wrapped,
 * the ten families took five lines of a phone and left no scale on the tab's first screen. The row reaches the window's
 * sides, under the navigation rail and the system bars, and keeps the page's padding and the screen's insets as
 * [startPadding] and [endPadding] inside the scroll, so the first and last chips line up with the page while what is
 * scrolled past them slides under the chrome rather than being cut off short of it.
 * [scrollState] is the screen's, kept for the session, since the row is disposed as it scrolls out of the grid and while a
 * search stands in its place.
 */
@Composable
internal fun ScaleFamilyRow(
    modifier: Modifier = Modifier,
    labels: List<String>,
    selectedIndex: Int,
    scrollState: ScrollState,
    startPadding: Dp,
    endPadding: Dp,
    onSelected: (Int) -> Unit,
) = Row(
    modifier = modifier
        .fillMaxWidth()
        .horizontalScroll(scrollState)
        .padding(start = startPadding, end = endPadding)
        .selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
) {
    labels.forEachIndexed { index, label ->
        SelectableChip(
            isSelected = index == selectedIndex,
            role = Role.RadioButton,
            onClick = { onSelected(index) },
        ) { contentColor ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}
