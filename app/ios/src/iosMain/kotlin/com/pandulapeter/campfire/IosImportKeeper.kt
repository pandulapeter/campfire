/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire

import com.pandulapeter.campfire.presentation.ui.platform.BackgroundNotificationScheduler
import com.pandulapeter.campfire.presentation.ui.platform.ImportNotification
import com.pandulapeter.campfire.presentation.ui.platform.ImportNotifier
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportWork
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportWorkState
import com.pandulapeter.campfire.presentation.ui.state.importing.importBytesOf
import com.pandulapeter.campfire.presentation.ui.state.importing.textOf
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps an import working while the app is not in front, the way [IosSyncNotifier] keeps a sync run: the work belongs
 * to the app ([ImportWork]), and this only asks iOS for the time it needs, following the work's state itself, since
 * Compose stops with the scene.
 *
 * On iOS 26 and later ([continuedTasks] is there) a continued processing task is submitted as the app is left while
 * the import works, and ended as the app comes back or the work goes idle: the system shows its progress and lets the
 * user stop it, which interrupts the import as the task's expiry does ([ImportWork.interrupt]: its staging deleted, and
 * Try again on the user's return). Leaving rather than starting is when, so that the many imports that end in a moment
 * in front (a pasted song) never put the system's progress up. A submit the system refuses, and every iOS before 26,
 * falls back on background time (`beginBackgroundTask`) as sync does: a few tens of seconds, whose expiry interrupts the
 * import too, the task ended in the handler itself, since an app that keeps one past its time is killed while one that
 * ends it is only suspended, with a write still running resuming on return.
 *
 * The Import write is never interrupted (`ImportWork.interrupt` leaves it alone): an expiry during it only lets the app
 * be suspended, and the write carries on when it comes back.
 */
class IosImportKeeper(
    private val work: ImportWork,
    private val continuedTasks: ContinuedImportTasks?,
) : ImportNotifier {

    private val scope = MainScope()
    private var words: ImportNotification? = null
    private var backgroundTask: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid
    private var hasContinuedTask = false

    /**
     * Set when the system ended the time it gave (or the user stopped the continued task) and cleared on the return to
     * the front, so that a write still running, which nothing interrupts, does not ask for more time it would not get,
     * or submit a task the user has just stopped.
     */
    private var isOutOfTime = false
    private var shownState = ImportWorkState.IDLE
    private val scheduler = BackgroundNotificationScheduler(
        scope = scope,
        updateInterval = 1.seconds,
        handoverGrace = 2.seconds,
        postUpdate = ::updateContinuedTask,
        release = ::release,
        isRunGoing = { work.state.value != ImportWorkState.IDLE },
    )

    /** The observers are never removed, since there is one keeper for the life of the process. */
    init {
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> holdWhileLeaving() }
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            isOutOfTime = false
            endContinuedTask()
        }
        scope.launch {
            combine(work.state, work.currentProgress) { state, _ -> state }.collect { state ->
                if (state == ImportWorkState.IDLE) {
                    if (hasContinuedTask || backgroundTask != UIBackgroundTaskInvalid) scheduler.scheduleRelease()
                } else {
                    scheduler.cancelRelease()
                    val isNewPhase = state != shownState
                    shownState = state
                    when {
                        continuedTasks == null -> beginBackgroundTask()
                        // Work that starts once the app is out of sight: a queued batch taken up after the one before.
                        UIApplication.sharedApplication.applicationState != UIApplicationState.UIApplicationStateActive -> holdWhileLeaving()
                    }
                    if (hasContinuedTask) scheduler.requestUpdate(immediately = isNewPhase)
                }
            }
        }
    }

    override fun onImportNotificationChanged(notification: ImportNotification) {
        words = notification
    }

    /** Asks for the time an import going on needs as the app is left, see the note on the class. */
    private fun holdWhileLeaving() {
        val state = work.state.value
        if (state == ImportWorkState.IDLE || isOutOfTime || hasContinuedTask || backgroundTask != UIBackgroundTaskInvalid) return
        val words = words
        val isSubmitted = continuedTasks != null && words != null && continuedTasks.submit(
            title = words.title,
            subtitle = words.textOf(state, work.currentProgress.value) ?: words.title,
            onExpired = ::onContinuedTaskExpired,
        )
        if (isSubmitted) {
            hasContinuedTask = true
            updateContinuedTask()
        } else {
            beginBackgroundTask()
        }
    }

    /**
     * Back in front the import screen shows the work, so the system's progress goes, as Android's notification does; it
     * is submitted again, under a fresh identifier, should the user leave before the work ends. Background time is kept,
     * as sync keeps it: it only counts while the app is out of sight.
     */
    private fun endContinuedTask() {
        if (!hasContinuedTask) return
        hasContinuedTask = false
        scheduler.cancelUpdate()
        continuedTasks?.finish()
    }

    /** The system or the user ended the task: the import stops cleanly rather than with the process. */
    private fun onContinuedTaskExpired() {
        if (!hasContinuedTask) return
        hasContinuedTask = false
        isOutOfTime = true
        scheduler.cancelUpdate()
        work.interrupt()
    }

    private fun updateContinuedTask() {
        if (!hasContinuedTask) return
        val words = words ?: return
        val state = work.state.value.takeIf { it != ImportWorkState.IDLE } ?: shownState
        val progress = work.currentProgress.value
        // Bytes where the phase counts them, else files: the system watches a continued task's progress move.
        val bytes = importBytesOf(state, progress)
        val completed = bytes?.completed ?: progress?.completed?.toLong() ?: 0L
        val total = if (bytes != null) bytes.total ?: 0L else progress?.total?.toLong() ?: 0L
        continuedTasks?.update(subtitle = words.textOf(state, progress) ?: words.title, completed = completed, total = total)
    }

    /**
     * The expiration handler is not optional, and ends the task itself, synchronously: one still held when iOS says the
     * time is up gets the app killed. It only asks the import to stop, never waits for it.
     */
    private fun beginBackgroundTask() {
        if (backgroundTask != UIBackgroundTaskInvalid || isOutOfTime) return
        backgroundTask = UIApplication.sharedApplication.beginBackgroundTaskWithName("import") {
            isOutOfTime = true
            work.interrupt()
            endBackgroundTask()
        }
    }

    private fun endBackgroundTask() {
        if (backgroundTask == UIBackgroundTaskInvalid) return
        UIApplication.sharedApplication.endBackgroundTask(backgroundTask)
        backgroundTask = UIBackgroundTaskInvalid
    }

    /** What keeps the app working, given back once no work has been going for the scheduler's grace. */
    private fun release() {
        endContinuedTask()
        endBackgroundTask()
    }
}
