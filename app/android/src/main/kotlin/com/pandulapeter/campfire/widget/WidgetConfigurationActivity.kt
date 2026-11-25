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
import androidx.activity.enableEdgeToEdge
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.pandulapeter.campfire.presentation.ui.widget.WidgetSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A widget's setup screen, which the launcher opens as the widget is added and whenever it is reconfigured
 * (`widgetFeatures`): [showSetup] shows the widget's own choices, and [apply] keeps them in its Glance state, draws it,
 * and closes. Leaving without applying cancels, which as the widget is added means it is not added. Before the app has
 * written a snapshot with the setup screens' texts there is nothing to show, so the widget is added as it is, and says
 * to open the app where it has no snapshot at all.
 */
abstract class WidgetConfigurationActivity : ComponentActivity() {

    /** The widget's kind, whose state is kept and which is drawn again. */
    protected abstract val widget: GlanceAppWidget

    /** The widget's kind as the log names it. */
    protected abstract val logName: String

    private var glanceId: GlanceId? = null
    private var isApplied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, result(appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            println("$logName widget setup opened without a widget")
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
            // Throws for an id no widget of the app has: one removed while this was open, or a stranger's, since the
            // launcher has to be able to start this.
            val id = try {
                GlanceAppWidgetManager(this@WidgetConfigurationActivity).getGlanceIdBy(appWidgetId)
            } catch (_: IllegalArgumentException) {
                println("$logName widget setup opened for a widget that does not exist")
                finish()
                return@launch
            }
            glanceId = id
            val snapshot = withContext(Dispatchers.IO) { WidgetSnapshotFile.read(this@WidgetConfigurationActivity) }
            if (snapshot == null || !snapshot.strings.hasSetupTexts) {
                println("$logName widget set up before the app wrote a snapshot with the setup texts")
                apply(logDescription = "as it was") {}
                return@launch
            }
            showSetup(snapshot = snapshot, state = widget.getAppWidgetState<Preferences>(this@WidgetConfigurationActivity, id))
        }
    }

    /** Shows the setup screen over [snapshot], its choices starting from the widget's [state]. */
    protected abstract fun showSetup(snapshot: WidgetSnapshot, state: Preferences)

    /** Keeps what [update] writes in the widget's state, draws the widget, and closes; a second call is ignored. */
    protected fun apply(logDescription: String, update: (MutablePreferences) -> Unit) {
        val id = glanceId ?: return
        if (isApplied) return
        isApplied = true
        lifecycleScope.launch {
            updateAppWidgetState(this@WidgetConfigurationActivity, id, update)
            widget.update(this@WidgetConfigurationActivity, id)
            println("$logName widget set up: $logDescription")
            setResult(RESULT_OK, result(GlanceAppWidgetManager(this@WidgetConfigurationActivity).getAppWidgetId(id)))
            finish()
        }
    }

    private fun result(appWidgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
