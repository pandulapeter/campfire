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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Whether the window is a phone held upright, whose song cards give their text every dp they can spare. */
internal val isNarrowSongCardWindow: Boolean
    @Composable get() = LocalWindowInfo.current.containerDpSize.width < NARROW_SONG_CARD_WINDOW_WIDTH

/** How far a song card's text starts from the card's edge, which a narrow window brings closer. */
internal val songCardContentPadding: Dp
    @Composable get() = if (isNarrowSongCardWindow) NARROW_SONG_CARD_CONTENT_PADDING else LIST_ITEM_KEYLINE

/**
 * Where the text of a song card starts, measured from the edge of the list: what a list's own rows between the cards (a
 * section header, a setlist's description) start their text at too, so the list reads down one line.
 */
internal val songCardTextKeyline: Dp
    @Composable get() = SONG_CARD_OUTER_PADDING + songCardContentPadding

/** A setlist's zero-based position is shown to players as a one-based prefix. */
internal fun songCardTitle(title: String, index: Int?): String = if (index == null) title else "${index + 1} - $title"

/** Below this window width a song card's text gets the narrower padding, the smaller dot and a second title line. */
private val NARROW_SONG_CARD_WINDOW_WIDTH = 480.dp
private val NARROW_SONG_CARD_CONTENT_PADDING = 8.dp

/** Facing card edges each contribute 4dp, matching the 4dp above and below each card. */
internal fun songCardPadding(itemIndex: Int, columnCount: Int): PaddingValues {
    val column = itemIndex % columnCount
    return PaddingValues(
        start = if (column == 0) SONG_CARD_OUTER_PADDING else SONG_CARD_INNER_PADDING,
        end = if (column == columnCount - 1) SONG_CARD_OUTER_PADDING else SONG_CARD_INNER_PADDING,
        top = SONG_CARD_VERTICAL_PADDING,
        bottom = SONG_CARD_VERTICAL_PADDING,
    )
}

/** The room between a card and the edge of the list, or the [FastScroller]'s column, on its outer side. */
internal val SONG_CARD_OUTER_PADDING = 8.dp
private val SONG_CARD_INNER_PADDING = 4.dp

/** The room a card keeps above and below itself, half of the gap between two rows of cards. */
internal val SONG_CARD_VERTICAL_PADDING = 4.dp
