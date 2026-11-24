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
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.pandulapeter.campfire.presentation.ui.widget.SetlistWidgetChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Asks which setlist a setlist widget shows, as it is added and whenever it is reconfigured (`widgetFeatures`): Next gig
 * or one of the snapshot's setlists ([SetlistWidgetChooser]). Leaving without a choice cancels, which as the widget is
 * added means it is not added. Before the app has ever written a snapshot there is nothing to choose from, so the widget
 * is set to Next gig at once, and says to open the app.
 */
class SetlistWidgetConfigurationActivity : ComponentActivity() {

    private var isChosen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, result)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            println("Setlist widget configuration opened without a widget")
            finish()
            return
        }
        // As the main activity does: the automatic styles would ask for a scrim under a three-button navigation bar.
        val barStyle = if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@SetlistWidgetConfigurationActivity).getGlanceIdBy(appWidgetId)
            val snapshot = withContext(Dispatchers.IO) { WidgetSnapshotFile.read(this@SetlistWidgetConfigurationActivity) }
            if (snapshot == null) {
                println("Setlist widget configured before the app wrote a snapshot")
                choose(glanceId = glanceId, fileName = null, result = result)
                return@launch
            }
            val pinnedFileName = SetlistWidget().getAppWidgetState<Preferences>(this@SetlistWidgetConfigurationActivity, glanceId)[WidgetState.PINNED_SETLIST]
            setContent {
                SetlistWidgetChooser(
                    snapshot = snapshot,
                    chosenFileName = pinnedFileName,
                    onChosen = { fileName -> lifecycleScope.launch { choose(glanceId = glanceId, fileName = fileName, result = result) } },
                )
            }
        }
    }

    /** Keeps [fileName] (null for Next gig) as the widget's setlist, draws the widget, and closes, a second choice ignored. */
    private suspend fun choose(glanceId: GlanceId, fileName: String?, result: Intent) {
        if (isChosen) return
        isChosen = true
        updateAppWidgetState(this, glanceId) { state ->
            if (fileName == null) state.remove(WidgetState.PINNED_SETLIST) else state[WidgetState.PINNED_SETLIST] = fileName
        }
        SetlistWidget().update(this, glanceId)
        println("Setlist widget configured: ${if (fileName == null) "next gig" else "pinned setlist"}")
        setResult(RESULT_OK, result)
        finish()
    }
}
