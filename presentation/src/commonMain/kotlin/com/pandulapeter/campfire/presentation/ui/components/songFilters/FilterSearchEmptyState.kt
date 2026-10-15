/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.songFilters

import com.pandulapeter.campfire.presentation.ui.components.controls.CONTROLS_PADDING
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * What a filter group shows in place of its chips while its search matches none of them. It is about as tall as the
 * line of chips it stands in for, so that the group does not jump as the last chip goes, and it is announced as it
 * appears, since a screen reader typing into the field has nothing else to tell it the chips are gone.
 */
@Composable
internal fun FilterSearchEmptyState(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    text: String,
) = FilterGroupPart(
    modifier = modifier,
    isVisible = isVisible,
) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = EMPTY_STATE_MIN_HEIGHT)
            .wrapContentHeight()
            .padding(horizontal = CONTROLS_PADDING)
            .semantics { liveRegion = LiveRegionMode.Polite },
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A chip's height, which the message takes the place of. */
private val EMPTY_STATE_MIN_HEIGHT = 32.dp
