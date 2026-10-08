/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

/**
 * The body of a song card next to its actions, both centered vertically. Not a [ListItem]: Material pins the content
 * of a three-line item to its top and grows it to 88dp, so a card whose labels made it three lines left a band of
 * empty card under them, and the actions, which Material top-aligns there too, would have been measured against a
 * body they did not share a center with. The paddings, the minimum heights and the text styles are the list item's.
 *
 * @param coverArtUrl The cover drawn at the start of the body ([CoverArtImage]), next to the headline and the supporting
 *   line only and as tall as the two of them.
 * @param labelsContent The row of labels under both, which starts from the keyline beneath the cover rather than next
 *   to it: a row of labels is as long as somebody chose to make it, and the cover's column is room it can use.
 */
@Composable
internal fun CenteredSongCardContent(
    modifier: Modifier = Modifier,
    coverArtUrl: String? = null,
    actions: (@Composable () -> Unit)?,
    headlineContent: @Composable () -> Unit,
    supportingContent: (@Composable () -> Unit)?,
    labelsContent: (@Composable () -> Unit)? = null,
) = Layout(
    modifier = modifier.fillMaxWidth(),
    contents = listOf(
        { actions?.let { ListItemActions(content = it) } },
        {
            Box(
                modifier = Modifier
                    .heightIn(min = if (supportingContent == null && labelsContent == null) SONG_CARD_ONE_LINE_MIN_HEIGHT else SONG_CARD_TWO_LINE_MIN_HEIGHT)
                    .padding(
                        horizontal = songCardContentPadding,
                        vertical = SONG_CARD_VERTICAL_CONTENT_PADDING,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (coverArtUrl != null) {
                            CoverArtImage(
                                modifier = Modifier.padding(end = SONG_CARD_COVER_GAP).size(SONG_CARD_COVER_SIZE),
                                url = coverArtUrl,
                                shape = MaterialTheme.shapes.small,
                            )
                        }
                        Column {
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                                ProvideTextStyle(MaterialTheme.typography.bodyLarge, headlineContent)
                            }
                            if (supportingContent != null) {
                                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                                    ProvideTextStyle(MaterialTheme.typography.bodyMedium, supportingContent)
                                }
                            }
                        }
                    }
                    if (labelsContent != null) {
                        // Under a title alone the labels are the second line and follow it directly, as the artist would. A line
                        // of text carries some room under its letters and a cover carries none, so the labels keep further
                        // away from a cover to stand as far from it as they do from an artist line.
                        Box(
                            modifier = Modifier.padding(
                                top = when {
                                    coverArtUrl != null -> SONG_CARD_LABELS_COVER_GAP
                                    supportingContent == null -> 0.dp
                                    else -> SONG_CARD_LABELS_GAP
                                },
                            ),
                        ) {
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium, labelsContent)
                            }
                        }
                    }
                }
            }
        },
    ),
) { (actionMeasurables, bodyMeasurables), constraints ->
    val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
    val actionPlaceable = actionMeasurables.firstOrNull()?.measure(looseConstraints)
    val actionWidth = actionPlaceable?.width ?: 0
    val actionReservation = if (actionPlaceable == null) 0 else actionWidth + LIST_ITEM_TRAILING_GAP.roundToPx()
    val bodyWidth = (constraints.maxWidth - actionReservation).coerceAtLeast(0)
    val bodyPlaceable = bodyMeasurables.single().measure(looseConstraints.copy(minWidth = bodyWidth, maxWidth = bodyWidth))
    val height = maxOf(bodyPlaceable.height, actionPlaceable?.height ?: 0)
        .coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(constraints.maxWidth, height) {
        bodyPlaceable.placeRelative(0, (height - bodyPlaceable.height) / 2)
        actionPlaceable?.placeRelative(constraints.maxWidth - LIST_ITEM_KEYLINE.roundToPx() - actionWidth, (height - actionPlaceable.height) / 2)
    }
}

/**
 * Whatever a card carries at its end: the overflow button, with the setlist assignments button beside it on the songs
 * screen and the drag handle on the setlists screen.
 * Move the controls slightly toward the card's edge without changing the width reserved for them in the body.
 */
@Composable
private fun ListItemActions(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Box(
    modifier = modifier.offset(x = LIST_ITEM_TRAILING_KEYLINE_ADJUSTMENT),
) {
    content()
}

private val LIST_ITEM_TRAILING_GAP = 16.dp

/** The height of a card that is only a title, and of one with anything under it, as Material's list items have them. */
private val SONG_CARD_ONE_LINE_MIN_HEIGHT = 56.dp

/** A card's cover as tall as its title and the line under it together: a bodyLarge and a bodyMedium line. */
private val SONG_CARD_COVER_SIZE = 44.dp

/** The room between a card's first two lines and the labels under them. */
private val SONG_CARD_LABELS_GAP = 4.dp

/** The room between a card's cover and the labels under it. */
private val SONG_CARD_LABELS_COVER_GAP = 8.dp

/** The room between a card's cover and its text. */
private val SONG_CARD_COVER_GAP = 12.dp

private val SONG_CARD_TWO_LINE_MIN_HEIGHT = 72.dp
private val SONG_CARD_VERTICAL_CONTENT_PADDING = 12.dp
