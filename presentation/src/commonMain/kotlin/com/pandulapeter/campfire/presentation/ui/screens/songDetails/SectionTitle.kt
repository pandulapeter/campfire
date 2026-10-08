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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.songLayout.UNNAMED_SECTION_HEADER

/**
 * A section's name, followed by the chevron that folds it where it can be folded; only the chevron for a section with
 * no name ([UNNAMED_SECTION_HEADER]), or the room for it where it cannot. [chevronDescription] names the chevron instead
 * of the default "Hide the section".
 */
@Composable
internal fun SectionTitle(
    modifier: Modifier = Modifier,
    header: String,
    toggle: FoldToggle?,
    style: TextStyle,
    chevronSize: Dp,
    isChevronAtEnd: Boolean = false,
    chevronDescription: String? = null,
) = Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
) {
    // An empty name is still laid out, so that a pill with only the chevron in it is as tall as the named ones.
    Text(
        modifier = if (isChevronAtEnd) Modifier.weight(1f) else Modifier,
        text = header,
        style = style,
        color = MaterialTheme.colorScheme.primary,
    )
    // The chevron's place is kept in an unnamed pill that has none, which would otherwise be a sliver.
    if (toggle == null && header == UNNAMED_SECTION_HEADER) Spacer(modifier = Modifier.size(chevronSize))
    toggle?.let {
        FoldChevron(
            modifier = Modifier.padding(start = if (header == UNNAMED_SECTION_HEADER) 0.dp else FOLD_CHEVRON_GAP).size(chevronSize),
            kind = null,
            isExpanded = it.isExpanded,
            tint = MaterialTheme.colorScheme.primary,
            description = chevronDescription,
        )
    }
}
