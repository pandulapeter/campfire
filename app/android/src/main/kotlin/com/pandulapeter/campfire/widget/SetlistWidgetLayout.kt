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
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.presentation.ui.widget.SetlistWidgetContent
import com.pandulapeter.campfire.presentation.ui.widget.WidgetSnapshot
import com.pandulapeter.campfire.presentation.ui.widget.setlistContent

/**
 * The setlist widget: the setlist's title, its date and how far away it is, its song count, and as many of its songs'
 * titles as the height has room for, or the snapshot's sentence saying why there is no setlist. The whole widget is one
 * tap target, opening the content's link (the app, before it ever wrote a snapshot).
 */
@Composable
internal fun SetlistWidgetLayout(
    snapshot: WidgetSnapshot?,
    pinnedFileName: String?,
    todayEpochDay: Long,
) {
    val content = snapshot?.setlistContent(pinnedFileName = pinnedFileName, todayEpochDay = todayEpochDay)
    Column(modifier = GlanceModifier.widgetBackground().clickable(openLinkAction(content?.link))) {
        when (content) {
            is SetlistWidgetContent.Shown -> Setlist(
                content = content,
                label = snapshot.strings.nextGig.takeIf { pinnedFileName == null },
                textAlign = textAlignOf(snapshot.isMirrored()),
            )
            is SetlistWidgetContent.Message -> WidgetMessage(text = content.text)
            null -> WidgetMessage(text = LocalContext.current.getString(R.string.widget_open_app))
        }
    }
}

/** [content], under [label] (Next gig, where the widget is not pinned to the setlist). */
@Composable
private fun Setlist(
    content: SetlistWidgetContent.Shown,
    label: String?,
    textAlign: TextAlign,
) {
    Text(
        modifier = GlanceModifier.fillMaxWidth(),
        text = label.orEmpty(),
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = textAlign),
    )
    Text(
        modifier = GlanceModifier.fillMaxWidth(),
        text = content.title,
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = textAlign),
    )
    Text(
        modifier = GlanceModifier.fillMaxWidth(),
        text = listOf(content.relativeDay, content.date).filter { it.isNotBlank() }.joinToString(" · "),
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, textAlign = textAlign),
    )
    Text(
        modifier = GlanceModifier.fillMaxWidth(),
        text = content.songCount,
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, textAlign = textAlign),
    )
    val songLines = SetlistWidgetMetrics.songLinesFitting(LocalSize.current.height.value)
    if (songLines > 0 && content.songTitles.isNotEmpty()) {
        Spacer(modifier = GlanceModifier.height(4.dp))
        // Glance draws only the first ten children of a container, so the titles go in columns of their own, ten at most.
        content.songTitles.take(songLines).chunked(MAX_CONTAINER_CHILDREN).forEach { titles ->
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                titles.forEach { title ->
                    Text(
                        modifier = GlanceModifier.fillMaxWidth(),
                        text = title,
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, textAlign = textAlign),
                    )
                }
            }
        }
    }
}

private const val MAX_CONTAINER_CHILDREN = 10
