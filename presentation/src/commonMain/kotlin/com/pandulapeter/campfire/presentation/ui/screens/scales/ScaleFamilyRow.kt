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
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.controls.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.components.controls.SelectableChip
import com.pandulapeter.campfire.presentation.ui.components.fadingHorizontalEdges

/**
 * The Scales tab's families as one line of chips scrolling sideways, a radio group like the key row above it. Wrapped,
 * the ten families took five lines of a phone and left no scale on the tab's first screen. The padding is inside the
 * scroll, so the first and last chips line up with the page, and the row fades at an end with more chips past it.
 * [scrollState] is the screen's, kept for the session, since the controls item that draws the row is disposed as it
 * scrolls out of the grid.
 */
@Composable
internal fun ScaleFamilyRow(
    modifier: Modifier = Modifier,
    labels: List<String>,
    selectedIndex: Int,
    scrollState: ScrollState,
    onSelected: (Int) -> Unit,
) = Row(
    modifier = modifier
        .fillMaxWidth()
        .fadingHorizontalEdges(scrollState)
        .horizontalScroll(scrollState)
        .padding(horizontal = 16.dp)
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
