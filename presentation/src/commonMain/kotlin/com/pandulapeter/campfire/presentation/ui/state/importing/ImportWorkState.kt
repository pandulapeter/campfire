/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

/** What the import's work is doing, see [ImportWork.state]. */
enum class ImportWorkState {

    /** Nothing: no import is under way, or one is waiting for the user (its review, preview, or failure), which is not work. */
    IDLE,

    /** Waiting for archives that arrived to be copied into the staging folder. */
    RECEIVING,

    /** Downloading the file an address or a shared link names. */
    DOWNLOADING,

    /** Reading and comparing a batch, archives unpacked, until its plan is ready. */
    UNPACKING,

    /** Writing what the review chose (or the demo library); never cancelled once started. */
    WRITING;

    /**
     * Whether [ImportWork.cancel] would end this work, which is what a platform offers Cancel for: everything before the
     * write, which leaves the library as it was.
     */
    val isCancellable get() = this == RECEIVING || this == DOWNLOADING || this == UNPACKING
}
