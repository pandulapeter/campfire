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
import com.pandulapeter.campfire.presentation.ui.platform.ImportKeepAlive
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
 * Compose stops with the scene. What is asked for and when, and what an expiry does, is [ImportKeepAlive]'s to decide:
 * a continued processing task on iOS 26 and later ([continuedTasks] is there), whose expiry or stop interrupts the
 * import, else background time, whose expiry only lets the app be suspended with the import paused until it is back.
 */
class IosImportKeeper(
    private val work: ImportWork,
    private val continuedTasks: ContinuedImportTasks?,
) : ImportNotifier {

    private val scope = MainScope()
    private var words: ImportNotification? = null
    private var backgroundTask: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid
    private var shownState = ImportWorkState.IDLE
    private val keepAlive: ImportKeepAlive = ImportKeepAlive(
        hasContinuedTasks = continuedTasks != null,
        actions = object : ImportKeepAlive.Actions {
            override fun submitContinuedTask() = submit()
            override fun finishContinuedTask() {
                scheduler.cancelUpdate()
                continuedTasks?.finish()
            }

            override fun beginBackgroundTime() = beginBackgroundTask()
            override fun endBackgroundTime() = endBackgroundTask()
            override fun interruptImport() {
                scheduler.cancelUpdate()
                work.interrupt()
            }
        },
    )
    private val scheduler: BackgroundNotificationScheduler = BackgroundNotificationScheduler(
        scope = scope,
        updateInterval = 1.seconds,
        handoverGrace = 2.seconds,
        postUpdate = ::updateContinuedTask,
        release = keepAlive::release,
        isRunGoing = { work.state.value != ImportWorkState.IDLE },
    )

    /** The observers are never removed, since there is one keeper for the life of the process. */
    init {
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> keepAlive.onLeaving(isWorking = work.state.value != ImportWorkState.IDLE) }
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> keepAlive.onActive() }
        scope.launch {
            combine(work.state, work.currentProgress) { state, _ -> state }.collect { state ->
                if (state == ImportWorkState.IDLE) {
                    if (keepAlive.isHolding) scheduler.scheduleRelease()
                } else {
                    scheduler.cancelRelease()
                    val isNewPhase = state != shownState
                    shownState = state
                    keepAlive.onWorking(isAppActive = UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive)
                    if (keepAlive.hasContinuedTask) scheduler.requestUpdate(immediately = isNewPhase)
                }
            }
        }
    }

    override fun onImportNotificationChanged(notification: ImportNotification) {
        words = notification
    }

    private fun submit(): Boolean {
        val words = words ?: return false
        val state = work.state.value
        val isSubmitted = continuedTasks?.submit(
            title = words.title,
            subtitle = words.textOf(state, work.currentProgress.value) ?: words.title,
            onExpired = keepAlive::onContinuedTaskExpired,
        ) == true
        if (isSubmitted) scope.launch { updateContinuedTask() }
        return isSubmitted
    }

    private fun updateContinuedTask() {
        if (!keepAlive.hasContinuedTask) return
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
     * The expiration handler is not optional, and gives the time back itself, synchronously: an app still holding it
     * when iOS says the time is up is killed, while one that gives it back is only suspended, its import paused.
     */
    private fun beginBackgroundTask() {
        if (backgroundTask != UIBackgroundTaskInvalid) return
        backgroundTask = UIApplication.sharedApplication.beginBackgroundTaskWithName("import") {
            keepAlive.onBackgroundTimeExpired()
        }
    }

    private fun endBackgroundTask() {
        if (backgroundTask == UIBackgroundTaskInvalid) return
        UIApplication.sharedApplication.endBackgroundTask(backgroundTask)
        backgroundTask = UIBackgroundTaskInvalid
    }
}
