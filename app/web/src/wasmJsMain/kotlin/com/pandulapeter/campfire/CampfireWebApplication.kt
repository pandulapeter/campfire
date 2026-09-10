/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
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
import kotlin.js.ExperimentalWasmJsInterop

private val dataModules
    get() = dataLocalSourceModule + dataRemoteSourceModule + dataRepositoryModule

// Compose empties the element it is given, so it gets one of its own: the loading screen next to it in
// index.html has to outlive the handover, and it is this app that decides when that is (see below).
@OptIn(ExperimentalComposeUiApi::class)
fun main() = ComposeViewport(viewportContainerId = "app") {
    KoinApplication(
        koinConfiguration { modules(dataModules + domainModule + presentationModule) }
    ) {
        CampfireWebApp()
        DismissLoadingScreen()
    }
}

/**
 * Tells index.html that the app is on the canvas, which is what fades its loading screen out and
 * finishes its progress bar. Waiting for the composition is not enough, because `withFrameNanos`
 * resumes while the frame it belongs to is still being assembled - so the frame after it is the first
 * one that is certainly drawn, and the fade uncovers the app rather than an empty page.
 */
@Composable
private fun DismissLoadingScreen() = LaunchedEffect(Unit) {
    repeat(2) { withFrameNanos { } }
    reportAppReady()
}

private fun reportAppReady() {
    js("window.campfireReady && window.campfireReady()")
}
