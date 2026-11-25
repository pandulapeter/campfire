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

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.pandulapeter.campfire.presentation.ui.widget.PlacedWidgets
import com.pandulapeter.campfire.presentation.ui.widget.WidgetPlacement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * The widgets placed, by the ids the launcher bound to each receiver. The receivers say when one is placed or removed
 * ([notifyChanged]), which reaches the app while it runs: a widget added from a setlist's Add to home screen gets its
 * covers without the app leaving the front.
 */
internal class WidgetPlacementImpl(private val context: Context) : WidgetPlacement {

    override suspend fun placedWidgets() = withContext(Dispatchers.IO) {
        val manager = AppWidgetManager.getInstance(context)
        fun isPlaced(receiver: Class<*>) = manager.getAppWidgetIds(ComponentName(context, receiver)).isNotEmpty()
        PlacedWidgets(
            hasRandomPicks = isPlaced(RandomPicksWidgetReceiver::class.java),
            hasSetlist = isPlaced(SetlistWidgetReceiver::class.java),
        )
    }

    override val changes: Flow<Unit> = changeEvents.asSharedFlow()

    companion object {

        private val changeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        /** Called by the receivers as a widget is placed, updated, or removed. */
        fun notifyChanged() {
            changeEvents.tryEmit(Unit)
        }
    }
}
