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

import androidx.activity.compose.setContent
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidget
import com.pandulapeter.campfire.presentation.ui.widget.SetlistWidgetSetup
import com.pandulapeter.campfire.presentation.ui.widget.WidgetSnapshot

/** Asks which setlist a setlist widget shows: Next gig or one of the snapshot's setlists ([SetlistWidgetSetup]). */
class SetlistWidgetConfigurationActivity : WidgetConfigurationActivity() {

    override val widget: GlanceAppWidget = SetlistWidget()
    override val logName = "Setlist"

    override fun showSetup(snapshot: WidgetSnapshot, state: Preferences) = setContent {
        SetlistWidgetSetup(
            snapshot = snapshot,
            initialFileName = state[WidgetState.PINNED_SETLIST],
            onDone = { fileName ->
                apply(logDescription = if (fileName == null) "next gig" else "pinned setlist") { widgetState ->
                    if (fileName == null) widgetState.remove(WidgetState.PINNED_SETLIST) else widgetState[WidgetState.PINNED_SETLIST] = fileName
                }
            },
        )
    }
}
