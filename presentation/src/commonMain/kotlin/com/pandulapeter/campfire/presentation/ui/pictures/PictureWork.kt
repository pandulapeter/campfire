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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlin.time.Duration.Companion.milliseconds

/**
 * Whether a picture the user added is being made to fit ([PictureFitter]), which re-encoding a large WebP or PNG on an
 * older phone can take seconds of, with nothing else on screen saying so until the snackbar. One for the whole app,
 * shared by every fitter, so that the editor's pictures, a missing picture's Add image, and pictures arriving alone are
 * one piece of work to the progress bar along the top of the window.
 */
internal class PictureWork {

    private val running = MutableStateFlow(0)

    /**
     * Whether the work is worth a sign: only once it has run for [SHOW_DELAY], so that a picture kept as it is, which
     * takes a moment, brings nothing up, and at once no longer when the last piece of work ends. Cold, and false until
     * then, which is also what a collector's first frame shows.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isShown: Flow<Boolean> = running
        .map { it > 0 }
        .distinctUntilChanged()
        .transformLatest { isRunning ->
            if (isRunning) delay(SHOW_DELAY)
            emit(isRunning)
        }
        .distinctUntilChanged()

    /** Runs [work] as part of this, however it ends. */
    suspend fun <T> track(work: suspend () -> T): T {
        running.update { it + 1 }
        try {
            return work()
        } finally {
            running.update { it - 1 }
        }
    }

    companion object {
        val SHOW_DELAY = 400.milliseconds
    }
}
