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

/**
 * iOS 26's continued processing tasks, which keep an import working once the user leaves the app and show its progress
 * in the system's own UI, with a way to stop it. Implemented in Swift (`ContinuedImportTasks.swift`), where the API can
 * be guarded by `#available` and weakly linked for the iOS 15.3 the app still supports; Swift hands one over only on iOS
 * 26 and later. Every call is on the main thread.
 */
interface ContinuedImportTasks {

    /**
     * Submits a task for the import going on, under a fresh identifier registered just before (one never registered,
     * or registered twice, ends the app). False where the system refused it (the simulator always does, a device may
     * when too many are running), for the caller to fall back on background time.
     *
     * @param onExpired Called on the main thread when the system or the user ended the task before [finish]; the task is
     *   already over by then.
     */
    fun submit(title: String, subtitle: String, onExpired: () -> Unit): Boolean

    /** Shows how far the import is: [completed] of [total], or indeterminate where [total] is not positive. */
    fun update(subtitle: String, completed: Long, total: Long)

    /** Ends the task submitted last, if it has not ended, as done; a task that has not been launched yet is withdrawn. */
    fun finish()
}
