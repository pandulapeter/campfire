/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pandulapeter.campfire.presentation.ui.update.LocalIsCoveredByRequiredUpdate

/**
 * Listens while the screen holding the tuner is started and [canListen] - the microphone known to be allowed, or asked
 * for by a tap where the platform cannot say - and stops the moment the screen stops or leaves, or a required update
 * covers it, so that the system's recording indicator is lit exactly while the tuner is on screen. Coming back from the
 * system's settings is a start, which listens without a tap where the permission was granted there.
 */
@Composable
internal fun TunerListeningEffect(
    canListen: Boolean,
    onListeningChanged: (Boolean) -> Unit,
) {
    val currentOnListeningChanged by rememberUpdatedState(onListeningChanged)
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val isStarted = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val isCovered = LocalIsCoveredByRequiredUpdate.current
    // Started on both, but stopped only by the lifecycle: a permission that turns out to be needed is the notice's to
    // say, and a listening the page's own button started is not to be stopped by the status catching up with it. The
    // screen a required update draws over the app is the exception: the app behind it is still composed and started,
    // and the microphone is not to be open under a screen that is not the tuner.
    LaunchedEffect(isStarted, canListen, isCovered) {
        when {
            isCovered -> currentOnListeningChanged(false)
            isStarted && canListen -> currentOnListeningChanged(true)
        }
    }
    LifecycleStartEffect(Unit) {
        onStopOrDispose { currentOnListeningChanged(false) }
    }
}
