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

import android.content.BroadcastReceiver
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import com.pandulapeter.campfire.presentation.ui.widget.DEFAULT_RANDOM_PICKS_COUNT
import com.pandulapeter.campfire.presentation.ui.widget.RandomPicksFilter
import com.pandulapeter.campfire.presentation.ui.widget.WidgetSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The keys of what each widget keeps in its own Glance state, so that two widgets of a kind differ. */
internal object WidgetState {

    /** When the widget was last asked to read the snapshot and the day again ([refreshAll]). */
    val REFRESHED_AT = longPreferencesKey("refreshedAt")

    /** The random picks widget's draw, kept as the seed that draws it ([RandomPicksWidget]). */
    val RANDOM_PICKS_SEED = longPreferencesKey("randomPicksSeed")

    /** When [RANDOM_PICKS_SEED] was drawn, in milliseconds since the epoch. */
    val RANDOM_PICKS_DRAWN_AT = longPreferencesKey("randomPicksDrawnAt")

    /** The random picks widget's own title, none for the snapshot's Random picks ([RandomPicksWidgetConfigurationActivity]). */
    val RANDOM_PICKS_TITLE = stringPreferencesKey("randomPicksTitle")

    /** How many songs the random picks widget draws, none for [DEFAULT_RANDOM_PICKS_COUNT]. */
    val RANDOM_PICKS_COUNT = intPreferencesKey("randomPicksCount")

    /** The tags the random picks widget draws from, none for every song ([RandomPicksFilter.tags]). */
    val RANDOM_PICKS_TAGS = stringSetPreferencesKey("randomPicksTags")

    /** The languages the random picks widget draws from, none for every song ([RandomPicksFilter.languages]). */
    val RANDOM_PICKS_LANGUAGES = stringSetPreferencesKey("randomPicksLanguages")

    /** The file name of the setlist the setlist widget was pinned to, none for Next gig. */
    val PINNED_SETLIST = stringPreferencesKey("pinnedSetlist")
}

/**
 * Redraws every widget of this kind from the snapshot and the day as they are now.
 *
 * A widget's composition outlives one drawing for a while, and an update while it is alive only recomposes it, which a
 * state that has not changed does not even do: so each widget's [WidgetState.REFRESHED_AT] is moved on first, which
 * [rememberWidgetSnapshot] reads, so that the day is read again too.
 */
/**
 * Redraws every widget of the receiver's kind, the process kept up for it ([BroadcastReceiver.goAsync]). The receivers
 * call it for `MY_PACKAGE_REPLACED`, which is how they **redraw after the app's update**: an update
 * puts every placed widget back to its loading layout, and nothing else draws it again before the hourly update, the
 * app's next snapshot finding the file unchanged.
 */
internal fun GlanceAppWidgetReceiver.refreshAllAsync(context: Context) {
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            glanceAppWidget.refreshAll(context)
        } finally {
            pendingResult.finish()
        }
    }
}

internal suspend fun GlanceAppWidget.refreshAll(context: Context) {
    GlanceAppWidgetManager(context).getGlanceIds(javaClass).forEach { glanceId ->
        updateAppWidgetState(context, glanceId) { it[WidgetState.REFRESHED_AT] = System.currentTimeMillis() }
        drawNow(context = context, glanceId = glanceId)
        update(context, glanceId)
    }
}

/**
 * The snapshot as the file holds it, from [WidgetSnapshotFile.snapshot], which the provider has loaded before composing:
 * Glance composes on the main thread, once for each of a widget's sizes.
 */
@Composable
internal fun rememberWidgetSnapshot(): WidgetSnapshot? {
    currentState(WidgetState.REFRESHED_AT)
    return WidgetSnapshotFile.snapshot.collectAsState().value
}
