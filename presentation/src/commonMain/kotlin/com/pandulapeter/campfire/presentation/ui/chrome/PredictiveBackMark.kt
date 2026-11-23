/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

/**
 * Whether the transition the specs last decided on follows a back gesture, a tab swap's included. Read when the gesture
 * completes, which is when the specs have last been asked about it, so that its pop is told apart from a back pressed
 * while a transition runs (see `Navigator.navigationGeneration`). Plain rather than snapshot state, since nothing draws
 * it.
 */
@Stable
internal class PredictiveBackMark {
    var isPredictiveBack = false
}

@Composable
internal fun rememberPredictiveBackMark() = remember { PredictiveBackMark() }
