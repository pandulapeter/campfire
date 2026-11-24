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

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Asks the launcher to put a setlist widget showing one setlist on the home screen, the setlists screen's Add to home
 * screen: the launcher asks the user where, in a popup of its own, and the widget it adds is pinned to the setlist by
 * [SetlistWidgetPinnedReceiver].
 */
internal object SetlistWidgetPinning {

    /** Whether the launcher lets an app ask: from Android 8, where the launcher supports it. */
    fun isSupported(context: Context) = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun request(context: Context, setlistFileName: String) {
        val callback = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, SetlistWidgetPinnedReceiver::class.java).putExtra(EXTRA_SETLIST_FILE_NAME, setlistFileName),
            // Mutable, since the launcher adds the new widget's id to it.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val isAsked = AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, SetlistWidgetReceiver::class.java), null, callback)
        println(if (isAsked) "Setlist widget pin requested" else "Setlist widget pin refused by the launcher")
    }

    const val EXTRA_SETLIST_FILE_NAME = "com.pandulapeter.campfire.extra.SETLIST_FILE_NAME"
}

/** Pins the widget the launcher added for [SetlistWidgetPinning.request] to its setlist, and draws it. */
class SetlistWidgetPinnedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val fileName = intent.getStringExtra(SetlistWidgetPinning.EXTRA_SETLIST_FILE_NAME)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || fileName == null) return
        val result = goAsync()
        scope.launch {
            try {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(context, glanceId) { it[WidgetState.PINNED_SETLIST] = fileName }
                SetlistWidget().update(context, glanceId)
                println("Setlist widget pinned to a setlist")
            } catch (_: IllegalArgumentException) {
                // Removed again before this arrived.
                println("Setlist widget pinned for a widget that no longer exists")
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
