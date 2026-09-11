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
import com.pandulapeter.campfire.data.repository.dataRepositoryModule
import com.pandulapeter.campfire.data.source.local.implementation.dataLocalSourceModule
import com.pandulapeter.campfire.data.source.remote.implementation.dataRemoteSourceModule
import com.pandulapeter.campfire.domain.implementation.domainModule
import com.pandulapeter.campfire.presentation.presentationModule
import com.pandulapeter.campfire.presentation.ui.CampfireWebApp
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

private val dataModules
    get() = dataLocalSourceModule + dataRemoteSourceModule + dataRepositoryModule

// Compose empties the element it is given, so it gets one of its own: the loading screen next to it in
// index.html has to outlive the handover, and it is CampfireWebApp that decides when that is.
@OptIn(ExperimentalComposeUiApi::class)
fun main() = ComposeViewport(viewportContainerId = "app") {
    KoinApplication(
        koinConfiguration { modules(dataModules + domainModule + presentationModule) }
    ) {
        CampfireWebApp()
    }
}
