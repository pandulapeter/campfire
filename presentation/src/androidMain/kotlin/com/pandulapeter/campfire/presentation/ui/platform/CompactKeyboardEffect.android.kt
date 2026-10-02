/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Restores the window's original status-bar visibility as soon as compact typing ends or its content leaves. */
@Composable
internal actual fun CompactKeyboardEffect(isEnabled: Boolean) {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: LocalActivity.current?.window
    DisposableEffect(window, isEnabled) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val wasVisible = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.statusBars()) != false
        val behavior = controller?.systemBarsBehavior
        if (isEnabled) {
            // The landscape keyboard leaves about 150 dp. Keeping a 48 dp status band as well as a 64 dp title
            // row cannot fit three text lines; transient bars keep the system controls reachable by a swipe.
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            if (isEnabled) {
                if (wasVisible) controller?.show(WindowInsetsCompat.Type.statusBars())
                if (behavior != null) controller?.systemBarsBehavior = behavior
            }
        }
    }
}
