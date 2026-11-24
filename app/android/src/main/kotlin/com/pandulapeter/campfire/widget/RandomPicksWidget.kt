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

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import kotlin.random.Random

/**
 * Songs drawn at random from the library, each opening its details screen, with a button that draws again. Every widget
 * keeps its own draw as a seed in its Glance state ([WidgetState.RANDOM_PICKS_SEED]), drawn again by the button
 * ([ShuffleAction]) and by the first drawing an hour after the last draw, which the provider's hourly update brings.
 */
internal class RandomPicksWidget : GlanceAppWidget() {

    /** One layout per number of rows, so that the widget lists as many songs as its height has room for. */
    override val sizeMode = SizeMode.Responsive((1..RandomPicksWidgetMetrics.MAX_ROWS).map { DpSize(MIN_WIDTH.dp, RandomPicksWidgetMetrics.heightFor(it).dp) }.toSet())

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val now = System.currentTimeMillis()
        val drawnAt = getAppWidgetState<Preferences>(context, id)[WidgetState.RANDOM_PICKS_DRAWN_AT]
        if (drawnAt == null || now - drawnAt !in 0 until DRAW_LIFETIME) {
            updateAppWidgetState(context, id) { it.drawRandomPicks(now) }
        }
        provideContent {
            WidgetTheme {
                RandomPicksWidgetLayout(
                    snapshot = rememberWidgetSnapshot(),
                    seed = currentState(WidgetState.RANDOM_PICKS_SEED) ?: 0L,
                )
            }
        }
    }

    private companion object {
        const val MIN_WIDTH = 100
        const val DRAW_LIFETIME = 60 * 60 * 1000L
    }
}

/** Draws new picks for the widget whose state this is, at [now]. */
internal fun MutablePreferences.drawRandomPicks(now: Long) {
    this[WidgetState.RANDOM_PICKS_SEED] = Random.nextLong()
    this[WidgetState.RANDOM_PICKS_DRAWN_AT] = now
}
