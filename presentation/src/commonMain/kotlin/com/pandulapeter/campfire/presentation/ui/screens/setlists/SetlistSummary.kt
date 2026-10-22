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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.setlists_pick_date
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.list.FastScrollerColumn
import com.pandulapeter.campfire.presentation.ui.components.labels.IconLabel
import com.pandulapeter.campfire.presentation.ui.components.labels.IconLabelMark
import com.pandulapeter.campfire.presentation.ui.components.list.sectionHeaderBottomGap
import com.pandulapeter.campfire.presentation.ui.components.songCard.SONG_CARD_VERTICAL_PADDING
import com.pandulapeter.campfire.presentation.ui.components.songCard.SONG_CARD_TEXT_KEYLINE

/**
 * What there is to read about a setlist under the row naming it: its countdown and running time, then its description. Not a
 * second line of the header, so that every header's name stands at one height whatever the setlist says about itself.
 *
 * @param countdown How far the setlist's day is, where the user asked for it ([countdownText]).
 * @param onCountdownClick Opens the calendar of the setlist's date; null where it cannot be changed (read only mode, an
 *   archived setlist).
 * @param runningTime How long the setlist's songs take ([runningTimeText]), after the countdown.
 * @param isSizeAnimated False while the list's width is still being found ([FastScrollerColumn.hasMeasuredWidth]): the
 *   description reflowing to it is not a change to animate, and would push the cards under it down over several frames.
 */
@Composable
internal fun SetlistSummary(
    modifier: Modifier = Modifier,
    countdown: String?,
    onCountdownClick: (() -> Unit)?,
    runningTime: String?,
    description: String,
    contentPadding: PaddingValues,
    isSizeAnimated: Boolean,
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
    // The outset leaves the countdown's pressed highlight the room above it that animateContentSize would clip away,
    // without moving anything in the list.
    Column(
        modifier = modifier
            .outset(vertical = LABEL_TOUCH_VERTICAL_PADDING)
            .then(if (isSizeAnimated) Modifier.animateContentSize() else Modifier)
            .padding(
                start = SONG_CARD_TEXT_KEYLINE,
                top = LABEL_TOUCH_VERTICAL_PADDING,
                end = SONG_CARD_TEXT_KEYLINE,
                bottom = (gap - SONG_CARD_VERTICAL_PADDING).coerceAtLeast(0.dp) + LABEL_TOUCH_VERTICAL_PADDING,
            ),
    ) {
        Row {
            val hasDescription = description.isNotBlank()
            SummaryLabel(text = countdown, mark = IconLabelMark.DATE, hasDescription = hasDescription, onClick = onCountdownClick)
            SummaryLabel(text = runningTime, mark = IconLabelMark.DURATION, hasDescription = hasDescription, onClick = null)
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

/**
 * One fact about the setlist, the countdown or the running time, which comes and goes with what it is made of. One that
 * does something on a tap lights up in a pill around it, laid out in its own size so that it stands where a label that
 * does nothing would.
 */
@Composable
private fun SummaryLabel(text: String?, mark: IconLabelMark, hasDescription: Boolean, onClick: (() -> Unit)?) {
    // Kept through its exit animation, which would otherwise run on an empty label.
    var lastText by remember { mutableStateOf(text.orEmpty()) }
    text?.let { lastText = it }
    // The pill's room is inside the AnimatedVisibility, whose expansion clips to its bounds, and taken back outside it.
    AnimatedVisibility(
        modifier = Modifier.outset(horizontal = LABEL_TOUCH_HORIZONTAL_PADDING, vertical = LABEL_TOUCH_VERTICAL_PADDING),
        visible = text != null,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally(),
    ) {
        // The room around it is its own rather than the column's or the row's spacing, which would also separate the
        // description from a label that is not there and push it away from the header.
        IconLabel(
            modifier = Modifier
                .padding(end = SUMMARY_LABEL_GAP, bottom = if (hasDescription) SUMMARY_LINE_GAP else 0.dp)
                .clip(CircleShape)
                .then(if (onClick == null) Modifier else Modifier.clickable(onClickLabel = stringResource(Res.string.setlists_pick_date), onClick = onClick))
                .padding(horizontal = LABEL_TOUCH_HORIZONTAL_PADDING, vertical = LABEL_TOUCH_VERTICAL_PADDING),
            text = lastText,
            mark = mark,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * Draws the content [horizontal] and [vertical] past every edge of the room it is laid out in, which is the room the
 * content would take without them.
 */
private fun Modifier.outset(horizontal: Dp = 0.dp, vertical: Dp) = layout { measurable, constraints ->
    val horizontalOutset = horizontal.roundToPx()
    val verticalOutset = vertical.roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = horizontalOutset * 2, vertical = verticalOutset * 2))
    layout(placeable.width - horizontalOutset * 2, placeable.height - verticalOutset * 2) {
        placeable.placeRelative(-horizontalOutset, -verticalOutset)
    }
}

/** The room between the summary and the first card under it, on top of what the header's row leaves. */
private val SUMMARY_BOTTOM_PADDING = 8.dp

/** The room between the labels and the description under them. */
private val SUMMARY_LINE_GAP = 8.dp

/** The room between a label and the one after it. */
private val SUMMARY_LABEL_GAP = 12.dp

/** The room around a label for its pressed highlight, beside its text. */
private val LABEL_TOUCH_HORIZONTAL_PADDING = 8.dp

/** The room around a label for its pressed highlight, above and below its text. */
private val LABEL_TOUCH_VERTICAL_PADDING = 4.dp
