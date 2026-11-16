/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.pictures

import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore

/**
 * The [MissingImagePick] whose picker is up, kept in [savedStateStore] for as long as it is. Android clears the view model
 * waiting for the picker with any Activity destroyed under it (process death, "Don't keep activities"), and hands the
 * picture to the next one, which knows from this which missing picture it was for (`OrphanedPicturesEffect`). The
 * saved state is written as the Activity stops, after the pick has started, and a write after that (the waiting pick
 * cancelled with its view model) no longer reaches it.
 */
internal class PendingMissingImagePick(
    private val savedStateStore: SavedStateStore,
) {

    fun keep(pick: MissingImagePick?) = savedStateStore.persist(KEY, pick)

    /** What [keep] kept, forgotten as it is read, so that it is never taken for the target of a later pick. */
    fun take(): MissingImagePick? = savedStateStore.restore<MissingImagePick>(KEY).also { keep(null) }

    private companion object {
        const val KEY = "missingImagePick"
    }
}
