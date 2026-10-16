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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import com.pandulapeter.campfire.presentation.ui.components.labels.IconLabel
import com.pandulapeter.campfire.presentation.ui.components.labels.IconLabelMark
import com.pandulapeter.campfire.presentation.ui.components.list.sectionHeaderBottomGap
import com.pandulapeter.campfire.presentation.ui.components.songCard.SONG_CARD_VERTICAL_PADDING
import com.pandulapeter.campfire.presentation.ui.components.songCard.songCardTextKeyline

/**
 * What there is to read about a setlist under the row naming it: its countdown and running time, then its description. Not a
 * second line of the header, so that every header's name stands at one height whatever the setlist says about itself.
 *
 * @param countdown How far the setlist's day is, where the user asked for it ([countdownText]).
 * @param runningTime How long the setlist's songs take ([runningTimeText]), after the countdown.
 */
@Composable
internal fun SetlistSummary(
    modifier: Modifier = Modifier,
    countdown: String?,
    runningTime: String?,
    description: String,
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
    // The summary stands as far from the first card as from the header's text above it, which is centered in a row of
    // a fixed height, so the room above depends on the text size. The card's
    // own padding counts towards it.
    val gap = sectionHeaderBottomGap() + SUMMARY_BOTTOM_PADDING
    Column(
        modifier = modifier
            .animateContentSize()
            .padding(
                start = songCardTextKeyline,
                end = songCardTextKeyline,
                bottom = (gap - SONG_CARD_VERTICAL_PADDING).coerceAtLeast(0.dp),
            ),
    ) {
        Row {
            val hasDescription = description.isNotBlank()
            SummaryLabel(text = countdown, mark = IconLabelMark.DATE, hasDescription = hasDescription)
            SummaryLabel(text = runningTime, mark = IconLabelMark.DURATION, hasDescription = hasDescription)
        }
        if (description.isNotBlank()) {
            Text(
                modifier = Modifier.clickable(enabled = isCompactHeight) { isExpanded = !isExpanded },
                maxLines = if (isCompactHeight && !isExpanded) 2 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One fact about the setlist, the countdown or the running time, which comes and goes with what it is made of. */
@Composable
private fun SummaryLabel(text: String?, mark: IconLabelMark, hasDescription: Boolean) {
    // Kept through its exit animation, which would otherwise run on an empty label.
    var lastText by remember { mutableStateOf(text.orEmpty()) }
    text?.let { lastText = it }
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally(),
    ) {
        // The room around it is its own rather than the column's or the row's spacing, which would also separate the
        // description from a label that is not there and push it away from the header.
        IconLabel(
            modifier = Modifier.padding(end = SUMMARY_LABEL_GAP, bottom = if (hasDescription) SUMMARY_LINE_GAP else 0.dp),
            text = lastText,
            mark = mark,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** The room between the summary and the first card under it, on top of what the header's row leaves. */
private val SUMMARY_BOTTOM_PADDING = 8.dp

/** The room between the labels and the description under them. */
private val SUMMARY_LINE_GAP = 8.dp

/** The room between a label and the one after it. */
private val SUMMARY_LABEL_GAP = 12.dp
