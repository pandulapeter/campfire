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
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.presentation.ui.widget.RandomPicksContent
import com.pandulapeter.campfire.presentation.ui.widget.RandomPicksFilter
import com.pandulapeter.campfire.presentation.ui.widget.WidgetSnapshot
import com.pandulapeter.campfire.presentation.ui.widget.randomPicksContent

/**
 * The random picks widget: a header with its [title] (the snapshot's Random picks where it has none of its own) and the
 * button that draws again where that can change the picks, then as many songs drawn by [seed] from those [filter] leaves
 * as the height has room for, or the snapshot's sentence where there is nothing to draw from. Anywhere but a song opens
 * the app.
 */
@Composable
internal fun RandomPicksWidgetLayout(
    snapshot: WidgetSnapshot?,
    seed: Long,
    title: String?,
    filter: RandomPicksFilter,
) {
    val rows = RandomPicksWidgetMetrics.rowsFitting(LocalSize.current.height.value)
    val content = snapshot?.randomPicksContent(count = rows, seed = seed, filter = filter)
    Column(modifier = GlanceModifier.widgetBackground().clickable(openLinkAction(snapshot?.homeLink))) {
        Header(
            title = title?.takeIf { it.isNotBlank() } ?: snapshot?.strings?.randomPicks ?: LocalContext.current.getString(R.string.widget_random_picks_name),
            shuffleDescription = snapshot?.strings?.drawNewPicks.takeIf { (content as? RandomPicksContent.Picks)?.canDrawAgain == true },
            isMirrored = snapshot.isMirrored(),
        )
        when (content) {
            is RandomPicksContent.Picks -> content.songs.forEach { song ->
                WidgetSongRow(title = song.title, artist = song.artist, number = null, link = song.link, isMirrored = snapshot.isMirrored())
            }
            is RandomPicksContent.Message -> WidgetMessage(text = content.text)
            null -> WidgetMessage(text = LocalContext.current.getString(R.string.widget_open_app))
        }
    }
}

/**
 * The widget's title, and the button that draws again where [shuffleDescription], its label, is given: after the title,
 * or before it where the widget [isMirrored].
 */
@Composable
private fun Header(
    title: String,
    shuffleDescription: String?,
    isMirrored: Boolean,
) = Row(
    modifier = GlanceModifier.fillMaxWidth().height(RandomPicksWidgetMetrics.HEADER_HEIGHT.dp).padding(horizontal = WIDGET_PADDING.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    if (isMirrored) ShuffleButton(shuffleDescription)
    Text(
        modifier = GlanceModifier.defaultWeight(),
        text = title,
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = textAlignOf(isMirrored)),
    )
    if (!isMirrored) ShuffleButton(shuffleDescription)
}

@Composable
private fun ShuffleButton(description: String?) {
    if (description != null) {
        CircleIconButton(
            modifier = GlanceModifier.size(RandomPicksWidgetMetrics.HEADER_HEIGHT.dp),
            imageProvider = ImageProvider(R.drawable.ic_widget_shuffle),
            contentDescription = description,
            onClick = actionRunCallback<ShuffleAction>(),
            backgroundColor = null,
            contentColor = GlanceTheme.colors.primary,
        )
    }
}
