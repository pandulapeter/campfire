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

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.pandulapeter.campfire.di.startCampfireDependencyGraph
import com.pandulapeter.campfire.presentation.ui.CampfireWebApp

// Compose empties the element it is given, so it gets one of its own: the loading screen next to it in
// index.html has to outlive the handover, and it is CampfireWebApp that decides when that is.
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startCampfireDependencyGraph()
    ComposeViewport(viewportContainerId = "app") {
        CampfireWebApp()
    }
}
