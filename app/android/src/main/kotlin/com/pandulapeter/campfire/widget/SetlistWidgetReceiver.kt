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
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The setlist widget's provider, named in the manifest with `xml/widget_setlist`. Besides the hourly update, the day
 * changing (or the clock or the time zone being set) redraws it at once, since "Tomorrow" becomes "Today" and a next gig
 * whose day has passed gives way to the next one. Android delivers the time and time zone broadcasts to a manifest
 * receiver; the date's is not among those it exempts from the background limits on every version, which the hourly
 * update covers.
 */
class SetlistWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = SetlistWidget()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in DAY_CHANGES) return super.onReceive(context, intent)
        println("Setlist widgets redrawn for ${intent.action}")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                glanceAppWidget.refreshAll(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val DAY_CHANGES = setOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
    }
}
