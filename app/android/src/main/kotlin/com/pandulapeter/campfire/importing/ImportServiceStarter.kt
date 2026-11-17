/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.importing

import android.app.Activity
import androidx.core.content.ContextCompat
import com.pandulapeter.campfire.presentation.ui.platform.ImportNotification
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportWorkState
import kotlinx.coroutines.flow.StateFlow

/**
 * Starts and dismisses [CampfireImportService] for [activity], as `SyncServiceStarter` does for sync: only once the app
 * is being left, since in front the activity keeps the process alive anyway and a notification for every import would
 * only repeat the import screen.
 *
 * @param workState The import's work (`ImportWork.state`), read as the app is left; the activity also reports each
 *   change through [onImportWorkChanged], for work that starts after that.
 */
internal class ImportServiceStarter(
    private val activity: Activity,
    private val workState: StateFlow<ImportWorkState>,
) {

    /** The words the composition last handed over, in the language chosen in the app. */
    private var notification: ImportNotification? = null

    /** The words the service was last started with, so that it is started again only for new ones (a language switch). */
    private var lastServiceWords: List<String>? = null

    /** Between a pause that was the user leaving - not a rotation - and the next resume. */
    private var isLeaving = false

    /** Whether the service has been asked to start since the app was last left; it reads as running only once it has the intent. */
    private var hasStartedServiceSinceLeaving = false

    fun onImportNotificationChanged(notification: ImportNotification) {
        this.notification = notification
        if (isLeaving) startServiceIfWorking()
    }

    /**
     * Work that starts after the app was left: a download's unpacking is covered by the service already going, but a
     * batch the queue takes up only now (one that waited behind another) needs it started. Android may refuse that once
     * the activity is no longer visible, which is logged and costs only the keeping alive.
     */
    fun onImportWorkChanged() {
        if (isLeaving) startServiceIfWorking()
    }

    /** Called from the activity's `onPause` where it is the user leaving rather than a rotation. */
    fun onLeaving() {
        isLeaving = true
        startServiceIfWorking()
    }

    /** Called from the activity's `onResume`: the import screen shows the work again, and the notification goes. */
    fun onReturned() {
        isLeaving = false
        hasStartedServiceSinceLeaving = false
        dismissService()
    }

    private fun startServiceIfWorking() {
        val notification = notification ?: return
        if (workState.value == ImportWorkState.IDLE) return
        val words = notification.toWords()
        if (words == lastServiceWords && (CampfireImportService.isRunning || hasStartedServiceSinceLeaving)) return
        try {
            ContextCompat.startForegroundService(activity, CampfireImportService.intent(context = activity, notification = notification))
            lastServiceWords = words
            hasStartedServiceSinceLeaving = true
        } catch (exception: Exception) {
            // A notification that cannot be shown must never take the import down with it: it carries on, it just stops
            // surviving the app being left.
            println("Could not start the import service: ${exception.message}")
        }
    }

    /** Takes the service down, if this activity or an earlier one in the process started it. Never cancels the import. */
    private fun dismissService() {
        if (lastServiceWords == null && !CampfireImportService.isRunning) return
        lastServiceWords = null
        try {
            activity.startService(CampfireImportService.dismissIntent(activity))
        } catch (exception: Exception) {
            println("Could not stop the import service: ${exception.message}")
        }
    }
}
