/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The two timings a phone shell's sync notification lives by, shared by the Android service and the iOS notifier: how
 * often a running count is posted, and how long whatever keeps the process alive outlives a run. What each shell
 * decides around them - when a run counts as chained, when there is anything to release - stays in the shell. Not
 * thread safe: every call, and [scope], is expected on the main thread.
 *
 * @param updateInterval The least time between two posted counts.
 * @param handoverGrace How long [release] waits after a run for the one chained behind it, see [scheduleRelease].
 * @param postUpdate Posts the notification from the latest counts, read when it is called.
 * @param release Gives back what keeps the process alive. May call [cancelRelease] and [cancelUpdate] itself.
 * @param isRunGoing Whether a run is going at the moment the grace ends.
 */
class SyncNotificationScheduler(
    private val scope: CoroutineScope,
    private val updateInterval: Duration,
    private val handoverGrace: Duration = 2.seconds,
    private val postUpdate: () -> Unit,
    private val release: () -> Unit,
    private val isRunGoing: () -> Boolean,
) {
    private var updateJob: Job? = null
    private var releaseJob: Job? = null

    /**
     * Posts the latest counts at most once per [updateInterval], from one delayed job that reads them when it fires: a
     * run finishes several files a second, and a job that only skipped updates inside the interval would leave the
     * count where the last burst stopped. On Android it is also what keeps the posts under the five notification
     * updates a second a package is allowed, over which the system sheds an arbitrary one of them rather than the
     * stale ones - a rate is what the limit is, so a rate is what this is.
     *
     * @param immediately For the step from preparing to counting, which changes the notification's shape rather than
     *   one of its numbers: it is posted at once, and an update still waiting is dropped.
     */
    fun requestUpdate(immediately: Boolean) {
        if (immediately) {
            cancelUpdate()
            postUpdate()
        } else if (updateJob?.isActive != true) {
            updateJob = scope.launch {
                delay(updateInterval)
                postUpdate()
            }
        }
    }

    fun cancelUpdate() {
        updateJob?.cancel()
        updateJob = null
    }

    /**
     * Calls [release] once no run has been going for [handoverGrace], unless a grace is already running. A run asked
     * for while another one was going starts within milliseconds of that one ending, and by then the app is often in
     * the background. On Android a service that let go the moment the first run ended could not be started again from
     * there, since Android 12 refuses a foreground service started from the background, and the run carrying the
     * user's latest change would go on in a process nothing keeps alive. On iOS an app whose background task had just
     * been ended would be suspended before that run's progress arrived, the run frozen in its first request. A run that
     * starts within the grace keeps it all: the shell calls [cancelRelease], and [isRunGoing] is asked again at the end
     * for one that started without the shell hearing of it in time.
     */
    fun scheduleRelease() {
        if (releaseJob?.isActive == true) return
        releaseJob = scope.launch {
            delay(handoverGrace)
            if (!isRunGoing()) {
                // Cleared before the call, since a release that cancels the grace would otherwise cancel this very job.
                releaseJob = null
                release()
            }
        }
    }

    fun cancelRelease() {
        releaseJob?.cancel()
        releaseJob = null
    }
}
