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
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.presentation.ui.widget.RandomPicksContent
import com.pandulapeter.campfire.presentation.ui.widget.WidgetSnapshot
import com.pandulapeter.campfire.presentation.ui.widget.randomPicksContent

/**
 * The random picks widget: a header with the button that draws again, then as many songs drawn by [seed] as the height
 * has room for, or the snapshot's sentence where there is nothing to draw from. Anywhere but a song opens the app.
 */
@Composable
internal fun RandomPicksWidgetLayout(
    snapshot: WidgetSnapshot?,
    seed: Long,
) {
    val rows = RandomPicksWidgetMetrics.rowsFitting(LocalSize.current.height.value)
    val content = snapshot?.randomPicksContent(count = rows, seed = seed)
    Column(modifier = GlanceModifier.widgetBackground().clickable(openLinkAction(snapshot?.homeLink))) {
        Header(
            title = snapshot?.strings?.randomPicks ?: LocalContext.current.getString(R.string.widget_random_picks_name),
            shuffleDescription = snapshot?.strings?.drawNewPicks.takeIf { content is RandomPicksContent.Picks },
        )
        when (content) {
            is RandomPicksContent.Picks -> content.songs.forEach { SongRow(song = it) }
            is RandomPicksContent.Message -> WidgetMessage(text = content.text)
            null -> WidgetMessage(text = LocalContext.current.getString(R.string.widget_open_app))
        }
    }
}

/** The widget's title, and the button that draws again where [shuffleDescription], its label, is given. */
@Composable
private fun Header(
    title: String,
    shuffleDescription: String?,
) = Row(
    modifier = GlanceModifier.fillMaxWidth().height(RandomPicksWidgetMetrics.HEADER_HEIGHT.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        modifier = GlanceModifier.defaultWeight(),
        text = title,
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold),
    )
    if (shuffleDescription != null) {
        CircleIconButton(
            modifier = GlanceModifier.size(RandomPicksWidgetMetrics.HEADER_HEIGHT.dp),
            imageProvider = ImageProvider(R.drawable.ic_widget_shuffle),
            contentDescription = shuffleDescription,
            onClick = actionRunCallback<ShuffleAction>(),
            backgroundColor = null,
            contentColor = GlanceTheme.colors.primary,
        )
    }
}

@Composable
private fun SongRow(song: WidgetSnapshot.Song) = Column(
    modifier = GlanceModifier
        .fillMaxWidth()
        .height(RandomPicksWidgetMetrics.ROW_HEIGHT.dp)
        .clickable(openLinkAction(song.link)),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        text = song.title,
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    )
    if (song.artist.isNotBlank()) {
        Text(
            text = song.artist,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
    }
}
