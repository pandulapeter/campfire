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
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.LazyListScope
import androidx.glance.layout.Box
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import com.pandulapeter.campfire.R

/**
 * A widget's songs under its header, filling the rest of the widget and scrolling, so that every one of them can be
 * reached whatever the widget's height (a plain container draws only its first ten children, and only what fits). It runs
 * to the widget's bottom edge, cut off by it, its own last row carrying the padding, so the widget's background leaves
 * out its bottom padding.
 */
@Composable
internal fun ColumnScope.WidgetSongList(content: LazyListScope.() -> Unit) = Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        // A row of the fade's height first, which is where the first song sits once it is scrolled back: a widget's list
        // cannot say how far it is scrolled, so the fade is always there, and only this is under it at rest.
        item { Spacer(modifier = GlanceModifier.height(EDGE_FADE_HEIGHT.dp)) }
        content()
        item { Spacer(modifier = GlanceModifier.height(WIDGET_PADDING.dp)) }
    }
    TopEdgeFade()
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

/** The fade's height, which is also the gap above the first song at rest. */
internal const val EDGE_FADE_HEIGHT = 8
