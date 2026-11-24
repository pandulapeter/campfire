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
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import java.time.LocalDate

/**
 * The next gig, or the setlist the widget was pinned to in its configuration ([SetlistWidgetConfigurationActivity],
 * kept in [WidgetState.PINNED_SETLIST]), the whole widget opening it. Today is the system's local date, read at every
 * drawing: the provider's hourly update and the receiver's date, time, and time zone broadcasts bring one past midnight.
 */
internal class SetlistWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        WidgetSnapshotFile.load(context)
        provideContent {
            val snapshot = rememberWidgetSnapshot()
            WidgetTheme(theme = snapshot?.theme) {
                SetlistWidgetLayout(
                    snapshot = snapshot,
                    pinnedFileName = currentState(WidgetState.PINNED_SETLIST),
                    todayEpochDay = LocalDate.now().toEpochDay(),
                )
            }
        }
    }
}
