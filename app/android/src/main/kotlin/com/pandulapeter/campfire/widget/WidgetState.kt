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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import com.pandulapeter.campfire.presentation.ui.widget.WidgetSnapshot

/** The keys of what each widget keeps in its own Glance state, so that two widgets of a kind differ. */
internal object WidgetState {

    /** When the widget was last asked to read the snapshot and the day again ([refreshAll]). */
    val REFRESHED_AT = longPreferencesKey("refreshedAt")

    /** The random picks widget's draw, kept as the seed that draws it ([RandomPicksWidget]). */
    val RANDOM_PICKS_SEED = longPreferencesKey("randomPicksSeed")

    /** When [RANDOM_PICKS_SEED] was drawn, in milliseconds since the epoch. */
    val RANDOM_PICKS_DRAWN_AT = longPreferencesKey("randomPicksDrawnAt")

    /** The file name of the setlist the setlist widget was pinned to, none for Next gig. */
    val PINNED_SETLIST = stringPreferencesKey("pinnedSetlist")
}

/**
 * Redraws every widget of this kind from the snapshot and the day as they are now.
 *
 * A widget's composition outlives one drawing for a while, and an update while it is alive only recomposes it, which a
 * state that has not changed does not even do: so each widget's [WidgetState.REFRESHED_AT] is moved on first, which is
 * what [rememberWidgetSnapshot] reads the file again for.
 */
internal suspend fun GlanceAppWidget.refreshAll(context: Context) {
    GlanceAppWidgetManager(context).getGlanceIds(javaClass).forEach { glanceId ->
        updateAppWidgetState(context, glanceId) { it[WidgetState.REFRESHED_AT] = System.currentTimeMillis() }
        update(context, glanceId)
    }
}

/** The snapshot as the file holds it, read again whenever the widget is refreshed. */
@Composable
internal fun rememberWidgetSnapshot(): WidgetSnapshot? {
    val context = LocalContext.current
    val refreshedAt = currentState(WidgetState.REFRESHED_AT)
    return remember(refreshedAt) { WidgetSnapshotFile.read(context) }
}
