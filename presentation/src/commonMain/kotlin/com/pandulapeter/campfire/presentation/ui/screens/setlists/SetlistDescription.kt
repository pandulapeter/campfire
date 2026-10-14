/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.setlists

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.songCard.SONG_CARD_VERTICAL_PADDING
import com.pandulapeter.campfire.presentation.ui.components.sectionHeaderBottomGap
import com.pandulapeter.campfire.presentation.ui.components.songCard.songCardTextKeyline

/**
 * The description of a setlist, the first thing to read about it under the row naming it.
 *
 * @param hasSubtitle Whether the header above has a subtitle, which decides how far its text stands from this one.
 */
@Composable
internal fun SetlistDescription(
    modifier: Modifier = Modifier,
    description: String,
    hasSubtitle: Boolean,
    contentPadding: PaddingValues,
) {
    // A description of four lines is most of what a short window shows, and with the keyboard
    // of a search up it pushes the setlist's songs out of sight, so there it starts at two
    // lines and opens on a tap. The keyboard counts, since that is when the room runs out.
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    // Derived, so that the keyboard sliding recomposes the item only as it crosses the threshold.
    val windowInfo = LocalWindowInfo.current
    val isCompactHeight by remember(windowInfo, contentPadding) {
        derivedStateOf { windowInfo.containerDpSize.height - contentPadding.calculateBottomPadding() < SHORT_WINDOW_HEIGHT }
    }
    // The description stands as far from the first card as from the header's text above it,
    // which is centered in a row of a fixed height, so the room above depends on whether the
    // header has a subtitle and on the text size. The card's own padding counts towards it.
    val gap = sectionHeaderBottomGap(hasSubtitle = hasSubtitle) + DESCRIPTION_TOP_PADDING
    Text(
        modifier = modifier
            .animateContentSize().clickable(enabled = isCompactHeight) { isExpanded = !isExpanded }
            .padding(
                start = songCardTextKeyline,
                end = songCardTextKeyline,
                top = DESCRIPTION_TOP_PADDING,
                bottom = (gap - SONG_CARD_VERTICAL_PADDING).coerceAtLeast(0.dp),
            ),
        maxLines = if (isCompactHeight && !isExpanded) 2 else Int.MAX_VALUE,
        overflow = TextOverflow.Ellipsis,
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The room between a setlist's description and the text of the header above it, on top of what the header leaves. */
private val DESCRIPTION_TOP_PADDING = 8.dp
