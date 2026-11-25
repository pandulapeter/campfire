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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

/**
 * The title remains one line even when its setlist number has grown to three digits, except in a narrow window: there
 * the cover and the trailing buttons leave a phone's card so little of its width that a title cut to one line often
 * could not be told from the next song's, so it may take a second.
 *
 * @param index The song's zero-based place in its setlist, shown before the title as a one-based number in the theme's
 * primary color, bold, as the Setlist widget draws it; null on a card listed outside a setlist.
 */
@Composable
internal fun ListItemHeadline(
    title: String,
    index: Int?,
) {
    val numberColor = MaterialTheme.colorScheme.primary
    val text = remember(title, index, numberColor) { songCardTitle(title, index, numberColor) }
    Text(text = text, maxLines = if (isNarrowSongCardWindow) 2 else 1, overflow = TextOverflow.Ellipsis)
}

private fun songCardTitle(title: String, index: Int?, numberColor: Color): AnnotatedString = buildAnnotatedString {
    if (index != null) {
        withStyle(SpanStyle(color = numberColor, fontWeight = FontWeight.Bold)) { append((index + 1).toString()) }
        append(" - ")
    }
    append(title)
}
