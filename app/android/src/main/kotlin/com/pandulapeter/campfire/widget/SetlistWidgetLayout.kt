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
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.presentation.ui.widget.SetlistWidgetContent
import com.pandulapeter.campfire.presentation.ui.widget.WidgetSnapshot
import com.pandulapeter.campfire.presentation.ui.widget.setlistContent

/**
 * The setlist widget: the setlist's title and how far away its day is, and its songs in a list that scrolls, as the
 * random picks widget lists its picks, each opening the setlist at it; or the snapshot's sentence saying why there is no
 * setlist. The rest of the widget opens the content's link (the app, before it ever wrote a snapshot).
 */
@Composable
internal fun SetlistWidgetLayout(
    snapshot: WidgetSnapshot?,
    pinnedFileName: String?,
    todayEpochDay: Long,
) {
    val content = snapshot?.setlistContent(pinnedFileName = pinnedFileName, todayEpochDay = todayEpochDay)
    // A setlist's list runs to the widget's bottom edge, cut off by it, its own last row carrying the padding.
    val bottomPadding = if (content is SetlistWidgetContent.Shown && content.songs.isNotEmpty()) 0 else WIDGET_PADDING
    Column(modifier = GlanceModifier.widgetBackground(bottomPadding = bottomPadding).clickable(openLinkAction(content?.link))) {
        when (content) {
            is SetlistWidgetContent.Shown -> Setlist(
                content = content,
                label = snapshot.strings.nextGig.takeIf { pinnedFileName == null },
                isMirrored = snapshot.isMirrored(),
            )
            is SetlistWidgetContent.Message -> WidgetMessage(text = content.text)
            null -> WidgetMessage(text = LocalContext.current.getString(R.string.widget_open_app))
        }
    }
}

/**
 * [content]: its header, the part that tells it from the random picks widget (its title under [label], Next gig where the
 * widget is not pinned to the setlist, then how far away its day is, the day itself adding nothing), over its songs in the
 * random picks widget's rows, each numbered by its place in the setlist.
 */
@Composable
private fun ColumnScope.Setlist(
    content: SetlistWidgetContent.Shown,
    label: String?,
    isMirrored: Boolean,
) {
    SetlistHeader(content = content, label = label, textAlign = textAlignOf(isMirrored))
    if (content.songs.isNotEmpty()) {
        Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                // A row of the fade's height first, which is where the first song sits once it is scrolled back: a
                // widget's list cannot say how far it is scrolled, so the fade is always there, and only this is under
                // it at rest.
                item { Spacer(modifier = GlanceModifier.height(EDGE_FADE_HEIGHT.dp)) }
                val numberDigits = content.songs.maxOf { it.number }.toString().length
                items(content.songs) { song ->
                    WidgetSongRow(
                        title = song.title,
                        artist = song.artist,
                        number = song.number,
                        link = song.link,
                        isMirrored = isMirrored,
                        numberDigits = numberDigits,
                    )
                }
                item { Spacer(modifier = GlanceModifier.height(WIDGET_PADDING.dp)) }
            }
            TopEdgeFade()
        }
    }
}

@Composable
private fun SetlistHeader(
    content: SetlistWidgetContent.Shown,
    label: String?,
    textAlign: TextAlign,
) = Column(modifier = GlanceModifier.fillMaxWidth().padding(horizontal = WIDGET_PADDING.dp)) {
    if (label != null) {
        Text(
            modifier = GlanceModifier.fillMaxWidth(),
            text = label,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = textAlign),
        )
    }
    Text(
        modifier = GlanceModifier.fillMaxWidth(),
        text = content.title,
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = textAlign),
    )
    if (content.relativeDay.isNotBlank()) {
        Text(
            modifier = GlanceModifier.fillMaxWidth(),
            text = content.relativeDay,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, textAlign = textAlign),
        )
    }
}

/**
 * The songs fading out under the header, as the app's lists do under its bars (`EdgeFade.kt`): the widget's background in
 * a gradient over them, the drawable's alpha in its color, a little shorter than the app's fade, since at rest it is the
 * gap under the header. Drawn over the list without taking touches, which reach the list under it. The bottom has none:
 * the list is cut off by the widget's edge, as a list running to the bottom of the app's window is.
 */
@Composable
private fun TopEdgeFade() = Image(
    modifier = GlanceModifier.fillMaxWidth().height(EDGE_FADE_HEIGHT.dp),
    provider = ImageProvider(R.drawable.widget_fade_top),
    contentDescription = null,
    contentScale = ContentScale.FillBounds,
    colorFilter = ColorFilter.tint(GlanceTheme.colors.widgetBackground),
)

private const val EDGE_FADE_HEIGHT = 20
