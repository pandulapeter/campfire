/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.songCard

import com.pandulapeter.campfire.presentation.ui.components.appBar.searchTravelSpec
import com.pandulapeter.campfire.presentation.ui.components.coverArt.CoverArtImage
import com.pandulapeter.campfire.presentation.ui.components.list.LIST_ITEM_KEYLINE
import com.pandulapeter.campfire.presentation.ui.components.list.LocalListItemTrailingKeylineAdjustment
import com.pandulapeter.campfire.presentation.ui.components.only
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp

/**
 * The body of a song card next to its actions, both centered vertically. Not a [ListItem]: Material pins the content
 * of a three-line item to its top and grows it to 88dp, so a card whose labels made it three lines left a band of
 * empty card under them, and the actions, which Material top-aligns there too, would have been measured against a
 * body they did not share a center with. The paddings, the minimum heights and the text styles are the list item's,
 * except at the end: next to actions the body runs up to them as they are drawn, moved towards the card's edge by
 * [LocalListItemTrailingKeylineAdjustment], and stops [SONG_CARD_ACTIONS_GAP] short of them. An icon button keeps its
 * icon well inside its touch target, so text that kept a list item's padding and gap from that target as well was cut
 * off with a wide empty band before the icon.
 *
 * @param coverArtUrl The cover drawn at the start of the body ([CoverArtImage]), next to the headline, the supporting
 *   line, and the details line only, and as tall as the lines drawn there.
 * @param isThreeLine Whether the supporting line and the details line are both drawn. The headline then takes the
 *   supporting line's line height, since its own leaves more room under the title than there is between the two lines
 *   below it, and the cover grows to the three lines. A card of two lines keeps the smaller cover and the lower card
 *   rather than standing half empty beside a cover made for three. Both follow the change as it animates, the artist
 *   fading in or out under an artist header as the sort or the search changes.
 * @param supportingContent The line under the headline, which a song card gives its artist alone.
 * @param detailsContent The line under that, in the smaller type of the values it holds.
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
    detailsContent: (@Composable () -> Unit)? = null,
    labelsContent: (@Composable () -> Unit)? = null,
    isThreeLine: Boolean = false,
) {
    val headlineStyle = MaterialTheme.typography.bodyLarge
    // On the spring the artist line folds out on and the search moves the list on, since the card's height follows all
    // three: the song list keeps the card at its top left still and slides the rest by that same spring, and a card that
    // grew on a faster one would push the cards under it ahead of the slide and let them drift back.
    val headlineLineHeight by animateFloatAsState(
        targetValue = if (isThreeLine) MaterialTheme.typography.bodyMedium.lineHeight.value else headlineStyle.lineHeight.value,
        animationSpec = searchTravelSpec(),
    )
    val coverSize by animateDpAsState(
        targetValue = if (isThreeLine) SONG_CARD_THREE_LINE_COVER_SIZE else SONG_CARD_TWO_LINE_COVER_SIZE,
        animationSpec = searchTravelSpec(visibilityThreshold = Dp.VisibilityThreshold),
    )
    val trailingKeylineAdjustment = LocalListItemTrailingKeylineAdjustment.current
    Layout(
        modifier = modifier.fillMaxWidth(),
        contents = listOf(
            { actions?.let { ListItemActions(content = it) } },
            {
                Box(
                    modifier = Modifier
                        .heightIn(min = if (supportingContent == null && detailsContent == null && labelsContent == null) SONG_CARD_ONE_LINE_MIN_HEIGHT else SONG_CARD_TWO_LINE_MIN_HEIGHT)
                        .padding(
                            start = songCardContentPadding,
                            end = if (actions == null) songCardContentPadding else 0.dp,
                            top = SONG_CARD_VERTICAL_CONTENT_PADDING,
                            bottom = SONG_CARD_VERTICAL_CONTENT_PADDING,
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (coverArtUrl != null) {
                                CoverArtImage(
                                    modifier = Modifier.padding(end = SONG_CARD_COVER_GAP).size(coverSize),
                                    url = coverArtUrl,
                                    shape = MaterialTheme.shapes.small,
                                )
                            }
                            Column {
                                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                                    ProvideTextStyle(headlineStyle.copy(lineHeight = headlineLineHeight.sp), headlineContent)
                                }
                                if (supportingContent != null) {
                                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                                        ProvideTextStyle(MaterialTheme.typography.bodyMedium, supportingContent)
                                    }
                                }
                                if (detailsContent != null) {
                                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                                        ProvideTextStyle(MaterialTheme.typography.labelMedium, detailsContent)
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
                                        supportingContent == null && detailsContent == null -> 0.dp
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
        val actionReservation = if (actionPlaceable == null) {
            0
        } else {
            actionWidth + (LIST_ITEM_KEYLINE - trailingKeylineAdjustment() + SONG_CARD_ACTIONS_GAP).roundToPx()
        }
        val bodyWidth = (constraints.maxWidth - actionReservation).coerceAtLeast(0)
        val bodyPlaceable = bodyMeasurables.single().measure(looseConstraints.copy(minWidth = bodyWidth, maxWidth = bodyWidth))
        val height = maxOf(bodyPlaceable.height, actionPlaceable?.height ?: 0)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, height) {
            bodyPlaceable.placeRelative(0, (height - bodyPlaceable.height) / 2)
            actionPlaceable?.placeRelative(constraints.maxWidth - LIST_ITEM_KEYLINE.roundToPx() - actionWidth, (height - actionPlaceable.height) / 2)
        }
    }
}

/**
 * Whatever a card carries at its end: the overflow button, with the setlist assignments button beside it on the songs
 * screen and the drag handle on the setlists screen.
 * Moved slightly toward the card's edge, which the body's measure follows so that its text runs up to them; both read
 * the move while laying out, since it follows the fast scroller's column on every frame of its spring.
 */
@Composable
private fun ListItemActions(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Box(
    modifier = LocalListItemTrailingKeylineAdjustment.current.let { adjustment ->
        modifier.offset { IntOffset(x = adjustment().roundToPx(), y = 0) }
    },
) {
    content()
}

/**
 * The room between a card's text and the touch target of its first action, on top of the empty space the action keeps
 * around its own icon (12dp in an icon button, 8dp in front of the drag handle).
 */
private val SONG_CARD_ACTIONS_GAP = 4.dp

/** The height of a card that is only a title, and of one with anything under it, as Material's list items have them. */
private val SONG_CARD_ONE_LINE_MIN_HEIGHT = 56.dp

/** A card's cover as tall as its title and the line under it: a bodyLarge and a bodyMedium line. */
private val SONG_CARD_TWO_LINE_COVER_SIZE = 44.dp

/** A card's cover as tall as its three lines: the title at a bodyMedium line's height, a bodyMedium, and a labelMedium. */
private val SONG_CARD_THREE_LINE_COVER_SIZE = 56.dp

/** The room between a card's lines of text and the labels under them. */
private val SONG_CARD_LABELS_GAP = 4.dp

/** The room between a card's cover and the labels under it. */
private val SONG_CARD_LABELS_COVER_GAP = 8.dp

/** The room between a card's cover and its text. */
private val SONG_CARD_COVER_GAP = 12.dp

private val SONG_CARD_TWO_LINE_MIN_HEIGHT = 72.dp
private val SONG_CARD_VERTICAL_CONTENT_PADDING = 8.dp
