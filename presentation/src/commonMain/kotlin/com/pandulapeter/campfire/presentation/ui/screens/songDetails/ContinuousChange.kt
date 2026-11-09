/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * True while [values] keep changing: from their second change within [CONTINUOUS_CHANGE_MILLIS] of the one before it
 * until they have held still for that long. The first change of a burst is indistinguishable from a single one, so it
 * still counts as discrete.
 */
@Composable
internal fun rememberContinuousChange(vararg values: Any): Boolean {
    var isContinuous by remember { mutableStateOf(false) }
    val tracker = remember { ContinuousChangeTracker() }
    LaunchedEffect(*values) {
        if (tracker.isFirst) {
            tracker.isFirst = false
            return@LaunchedEffect
        }
        // A change that arrives while the delay of the one before it was still running, which this restart cancelled,
        // is the second of a burst.
        if (tracker.isRecent) isContinuous = true
        tracker.isRecent = true
        delay(CONTINUOUS_CHANGE_MILLIS)
        tracker.isRecent = false
        isContinuous = false
    }
    return isContinuous
}

/** What [rememberContinuousChange] remembers between changes. Not state, since only its effect reads it. */
private class ContinuousChangeTracker {
    var isFirst = true
    var isRecent = false
}

private const val CONTINUOUS_CHANGE_MILLIS = 200L
