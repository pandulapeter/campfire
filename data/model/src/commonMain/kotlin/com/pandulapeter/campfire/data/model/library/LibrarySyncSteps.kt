/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.library

import com.pandulapeter.campfire.data.model.sync.SyncSummary

/** What Campfire keeps in the cloud folder besides its library files, by the ids [SyncSummary.failedSteps] names them. */
object LibrarySyncSteps {

    /** `preferences.json`, the per-song overrides, chord shapes, and tag colors every device shares. */
    const val PREFERENCES = "preferences"

    /** The shared links' copies, whose failure never makes a run look unfinished: the library is still in step. */
    const val SHARED_COPIES = "sharedCopies"
}

/**
 * Whether the run could not settle the preferences every device shares, which are not a library file and are named as
 * what they are rather than as one.
 */
val SyncSummary.havePreferencesFailed get() = LibrarySyncSteps.PREFERENCES in failedSteps
