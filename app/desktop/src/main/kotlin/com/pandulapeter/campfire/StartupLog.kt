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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.awt.ComposeWindow
import java.lang.management.ManagementFactory
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One line a desktop bug report can be read by: how the window is drawn, on what, and how long the start took.
 */
@Composable
internal fun StartupLogEffect(window: ComposeWindow) {
    LaunchedEffect(window) {
        withFrameNanos { }
        val sinceStart = ProcessHandle.current().info().startInstant().map { Duration.between(it, Instant.now()).toMillis() }
        // A Swing component's state, so read on the event thread rather than inside the IO block below.
        val renderApi = window.renderApi
        withContext(Dispatchers.IO) {
            val runtime = Runtime.getRuntime()
            println(
                "Started in ${sinceStart.map { "$it ms" }.orElse("an unknown time")}: $renderApi rendering, " +
                    "Java ${System.getProperty("java.runtime.version")} (${System.getProperty("java.vm.info")}), " +
                    "${ManagementFactory.getGarbageCollectorMXBeans().joinToString { it.name }}, " +
                    "${runtime.availableProcessors()} processors, ${runtime.maxMemory() / (1024 * 1024)} MB heap at most",
            )
        }
    }
}
