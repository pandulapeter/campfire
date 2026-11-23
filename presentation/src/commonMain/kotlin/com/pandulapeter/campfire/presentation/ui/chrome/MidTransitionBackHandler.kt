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
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 * Takes a back gesture started while a navigation transition is still running away from `NavDisplay`'s own handler,
 * and goes back once it is let go of. Navigation 3 follows a gesture by seeking its scene transition towards the screen
 * underneath, and a seek begun while that transition is still animating restarts the slides under way from where they
 * began: the card that was sliding in plays its whole slide again inside the strip the finger has uncovered, and the
 * transition state the seek leaves behind spoils the next transition as well.
 *
 * The back this goes is an ordinary one, which retargets the running slide from wherever it has got to, so going back
 * is as quick as ever; only following the finger waits until the screen has landed. It must be composed after
 * `NavDisplay`, so that it is heard before NavDisplay's handler while it is enabled. A gesture it has taken stays with it
 * until it is let go of or called off, even if the transition ends meanwhile.
 */
@Composable
internal fun MidTransitionBackHandler(
    isTransitionRunning: Boolean,
    canGoBack: Boolean,
    onBack: () -> Unit,
) = NavigationBackHandler(
    state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
    isBackEnabled = isTransitionRunning && canGoBack,
    onBackCompleted = onBack,
)
