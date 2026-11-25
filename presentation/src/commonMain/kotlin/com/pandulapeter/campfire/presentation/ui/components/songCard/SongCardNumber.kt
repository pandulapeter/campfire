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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A song's one-based place in its setlist, in a column of its own at the start of the card, centered in it both ways (on
 * the card's lines like the cover beside it, and across the column's width), bold in the theme's primary color, as the Setlist widget draws it. The column is as wide as
 * [digits] digits, the most a number of the setlist has (sized by hidden zeros in the same tabular figures), so that every
 * title and cover of a setlist starts in one line rather than a tenth song's pushing its own along.
 */
@Composable
internal fun SongCardNumber(
    modifier: Modifier = Modifier,
    number: Int,
    digits: Int,
) {
    val style = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
    )
    Box(
        modifier = modifier.padding(end = SONG_CARD_NUMBER_GAP),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            modifier = Modifier.alpha(0f).clearAndSetSemantics {},
            text = "0".repeat(maxOf(digits, number.toString().length)),
            style = style,
            maxLines = 1,
        )
        Text(
            text = number.toString(),
            style = style,
            maxLines = 1,
        )
    }
}

/** How many digits the largest of a setlist's [count] numbers has, which its cards' number columns are all as wide as. */
internal fun songCardNumberDigits(count: Int) = maxOf(count, 1).toString().length

/** The room between a card's number and its cover, or its text where it has none: what the cover keeps from the text. */
private val SONG_CARD_NUMBER_GAP = 12.dp
