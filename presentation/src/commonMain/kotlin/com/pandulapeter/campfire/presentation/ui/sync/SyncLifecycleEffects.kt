/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/**
 * Keeps the library in step with what other devices do while this one is used beside them. Another device may have
 * changed the cloud folder while the app was away, so a run starts whenever it comes back to the front - ON_START,
 * which is iOS entering the foreground and the desktop window being restored, and not ON_RESUME, which iOS also sends
 * after Control Center or a notification - and the folder is watched for as long as it stays there. The first start is
 * the launch, whose run the restore starts.
 */
@Composable
internal fun SyncLifecycleEffects(syncController: SyncController) {
    var hasStartedBefore by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (hasStartedBefore) syncController.onAppReturned() else hasStartedBefore = true
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, syncController) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { syncController.watchRemoteChanges() }
    }
}
