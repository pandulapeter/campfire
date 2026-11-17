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

/**
 * What the iOS shell asks of the system for an import that works while the app is not in front (`IosImportKeeper`),
 * decided here as plain logic so that it can be tested; the shell only carries the decisions out ([Actions]). Not
 * thread safe: every call is expected on the main thread.
 *
 * - **iOS 26 and later** ([hasContinuedTasks]): a continued processing task is submitted as the app is left while the
 *   import works, and finished as the app comes back or the work goes idle. Its expiry - the user stopping it in the
 *   system's progress, or the system ending it - interrupts the import (its staging deleted, Try again on the user's
 *   return), since that is a stop the user or the system asked for. A refused submit falls back on background time.
 * - **Before 26**, and after a refused submit: background time, held from the moment the work starts as sync's is. Its
 *   expiry only ends it, at once, and never interrupts the import: the app is then suspended with the work paused, and
 *   the work goes on when the app is back in front, as it always did. Interrupting would gain nothing - a process killed
 *   while suspended loses only the preparation, whose staging the next launch deletes - and would lose an import the
 *   user only stepped away from, the first run's demo library among them.
 *
 * Once the system has ended what it gave, nothing more is asked for until the app is active again: a write still
 * running would otherwise ask for time it would not get, or resubmit a task the user has just stopped.
 */
class ImportKeepAlive(
    private val hasContinuedTasks: Boolean,
    private val actions: Actions,
) {

    /** What the shell does on the system's side; each is only called when it is due. */
    interface Actions {

        /** Submits a continued processing task; false where the system refused it. */
        fun submitContinuedTask(): Boolean

        fun finishContinuedTask()

        fun beginBackgroundTime()

        /** Ends the background time, synchronously: the expiration handler that calls it must not return holding it. */
        fun endBackgroundTime()

        fun interruptImport()
    }

    var hasContinuedTask = false
        private set

    var hasBackgroundTime = false
        private set

    /** Whether anything is held, which is what the shell releases once the work has been idle for its grace. */
    val isHolding get() = hasContinuedTask || hasBackgroundTime

    private var isOutOfTime = false

    /**
     * Called for every change of the work while it is not idle. Before 26 the time is held from the start; on 26 it is
     * asked for only once the app is left, so that an import that ends in a moment in front (a pasted song) never puts
     * the system's progress up, except for work that starts while the app is already out of sight (a queued batch).
     */
    fun onWorking(isAppActive: Boolean) {
        if (!hasContinuedTasks) beginBackgroundTime() else if (!isAppActive) onLeaving(isWorking = true)
    }

    /** Called as the app resigns active, with whether the import works at that moment. */
    fun onLeaving(isWorking: Boolean) {
        if (!isWorking || isOutOfTime || isHolding) return
        if (hasContinuedTasks && actions.submitContinuedTask()) {
            hasContinuedTask = true
        } else {
            beginBackgroundTime()
        }
    }

    /**
     * Back in front the import screen shows the work, so the system's progress goes, as Android's notification does; a
     * new task is submitted should the user leave again. Background time is kept, as sync keeps it: it only counts while
     * the app is out of sight.
     */
    fun onActive() {
        isOutOfTime = false
        finishContinuedTask()
    }

    /** The user stopped the continued task in the system's progress, or the system ended it: the import is interrupted. */
    fun onContinuedTaskExpired() {
        if (!hasContinuedTask) return
        hasContinuedTask = false
        isOutOfTime = true
        actions.interruptImport()
    }

    /** iOS ended the background time: given back at once, the import paused with the app until it is back in front. */
    fun onBackgroundTimeExpired() {
        isOutOfTime = true
        endBackgroundTime()
    }

    /** Gives back everything held, once the work has been idle for the shell's grace. */
    fun release() {
        finishContinuedTask()
        endBackgroundTime()
    }

    private fun finishContinuedTask() {
        if (!hasContinuedTask) return
        hasContinuedTask = false
        actions.finishContinuedTask()
    }

    private fun beginBackgroundTime() {
        if (hasBackgroundTime || isOutOfTime) return
        hasBackgroundTime = true
        actions.beginBackgroundTime()
    }

    private fun endBackgroundTime() {
        if (!hasBackgroundTime) return
        hasBackgroundTime = false
        actions.endBackgroundTime()
    }
}
