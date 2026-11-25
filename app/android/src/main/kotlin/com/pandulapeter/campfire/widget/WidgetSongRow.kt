/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

/**
 * A song both widgets list: its [title] and [artist] (none where blank), after its [number] in a setlist where there is
 * one, opening [link]. The row spans the widget from edge to edge, so that its touch feedback does too, and carries the
 * widget's side padding inside itself; where the widget [isMirrored], the number comes after the text.
 */
@Composable
internal fun WidgetSongRow(
    title: String,
    artist: String,
    number: Int?,
    link: String,
    isMirrored: Boolean,
) = Row(
    modifier = GlanceModifier
        .fillMaxWidth()
        .height(RandomPicksWidgetMetrics.ROW_HEIGHT.dp)
        .clickable(openLinkAction(link))
        .padding(horizontal = WIDGET_PADDING.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    val hasArtist = artist.isNotBlank()
    if (number != null && !isMirrored) {
        SongNumber(number = number, hasArtist = hasArtist, isMirrored = false)
        Spacer(modifier = GlanceModifier.width(NUMBER_GAP.dp))
    }
    Column(modifier = GlanceModifier.defaultWeight()) {
        val textAlign = textAlignOf(isMirrored)
        Text(
            modifier = GlanceModifier.fillMaxWidth(),
            text = title,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = TITLE_SIZE.sp, fontWeight = FontWeight.Medium, textAlign = textAlign),
        )
        if (hasArtist) {
            Text(
                modifier = GlanceModifier.fillMaxWidth(),
                text = artist,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = ARTIST_SIZE.sp, textAlign = textAlign),
            )
        }
    }
    if (number != null && isMirrored) {
        Spacer(modifier = GlanceModifier.width(NUMBER_GAP.dp))
        SongNumber(number = number, hasArtist = hasArtist, isMirrored = true)
    }
}

/**
 * A setlist song's number, its digits against the text so that 9 and 10 end in one line, on the title's line: a widget
 * has no baseline alignment, so where an artist line follows the title, an empty line of its size under the number keeps
 * the two columns of one height and the number level with the title.
 */
@Composable
private fun SongNumber(
    number: Int,
    hasArtist: Boolean,
    isMirrored: Boolean,
) = Column(modifier = GlanceModifier.width(NUMBER_WIDTH.dp)) {
    val textAlign = textAlignOf(!isMirrored)
    Text(
        modifier = GlanceModifier.fillMaxWidth(),
        text = number.toString(),
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = TITLE_SIZE.sp, fontWeight = FontWeight.Bold, textAlign = textAlign),
    )
    if (hasArtist) {
        Text(
            modifier = GlanceModifier.fillMaxWidth(),
            text = " ",
            maxLines = 1,
            style = TextStyle(fontSize = ARTIST_SIZE.sp),
        )
    }
}

private const val NUMBER_WIDTH = 22
private const val NUMBER_GAP = 10
private const val TITLE_SIZE = 14
private const val ARTIST_SIZE = 12
