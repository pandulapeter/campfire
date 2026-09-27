/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.theme

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme
import kotlin.time.Duration.Companion.seconds

/**
 * Polls [currentSystemTheme], since Compose on the JVM reads the system's appearance once, when the window is created,
 * and hears nothing of a change after that - and neither AWT nor Skiko has a notification to wait for instead.
 *
 * One loop for the whole app however many compositions ask, off the main thread, and only while one of them is at
 * least started: a minimized window stops it, and restoring it picks up a change made meanwhile. A visible window
 * without the focus keeps asking, since that is how the change is made - in the system's settings, with Campfire in
 * view behind them. It is asked once a second rather than several times, which is still quick for a change that is
 * then faded into anyway and is what keeps an idle laptop from being woken for it, and it is only asked while the
 * "System" theme is selected, which is the only composition that reads it. Linux answers [SystemTheme.UNKNOWN],
 * which is taken as light, the way Compose's own [androidx.compose.foundation.isSystemInDarkTheme] takes it.
 */
@Composable
internal actual fun isSystemInDarkThemeLive() = systemDarkTheme.collectAsStateWithLifecycle().value

private val POLL_INTERVAL = 1.seconds

private val systemDarkTheme: StateFlow<Boolean> = flow {
    while (true) {
        emit(currentSystemTheme == SystemTheme.DARK)
        delay(POLL_INTERVAL)
    }
}.stateIn(
    scope = CoroutineScope(Dispatchers.Default),
    started = SharingStarted.WhileSubscribed(),
    initialValue = currentSystemTheme == SystemTheme.DARK,
)
