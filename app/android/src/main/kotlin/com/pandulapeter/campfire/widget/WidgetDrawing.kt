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
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Draws the widget [glanceId] from this process at once, besides the [GlanceAppWidget.update] that redraws it through
 * Glance's session.
 *
 * Glance runs a session as a WorkManager job, which the system holds back for an app in the background, on a Galaxy for
 * half a minute; and the app is in the background just when it matters most: a theme picked right before leaving, and
 * the launcher icon's switch, after which Android puts every widget of the app back to its loading layout and asks for
 * an update ([drawNowAsync]). A composition run here once ([compose]) needs no job.
 */
internal suspend fun GlanceAppWidget.drawNow(context: Context, glanceId: GlanceId) {
    try {
        val remoteViews = compose(context = context, id = glanceId)
        AppWidgetManager.getInstance(context).updateAppWidget(GlanceAppWidgetManager(context).getAppWidgetId(glanceId), remoteViews)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        // Another drawing of the same widget running at the time is one reason (Glance allows one at a time); the
        // session's update still comes.
        println("Could not draw a widget at once: ${exception.message}")
    }
}

/**
 * Draws the widgets [appWidgetIds] at once ([drawNow]), on a scope of its own: the receiver's one asynchronous result is
 * Glance's, taken by the update it starts.
 */
internal fun GlanceAppWidget.drawNowAsync(context: Context, appWidgetIds: IntArray) {
    CoroutineScope(Dispatchers.Default).launch {
        val manager = GlanceAppWidgetManager(context)
        appWidgetIds.forEach { drawNow(context = context, glanceId = manager.getGlanceIdBy(it)) }
    }
}
