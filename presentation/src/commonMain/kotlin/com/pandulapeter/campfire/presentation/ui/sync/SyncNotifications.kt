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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.sync.SyncProgress
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_sync_cancel
import com.pandulapeter.campfire.presentation.resources.settings_sync_notification_channel
import com.pandulapeter.campfire.presentation.resources.settings_sync_notification_title
import com.pandulapeter.campfire.presentation.resources.settings_sync_preparing
import com.pandulapeter.campfire.presentation.resources.settings_sync_progress
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.platform.LocalSyncNotifier
import com.pandulapeter.campfire.presentation.ui.platform.SyncNotification
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Tells the platform shell what a running sync should look like while the app is not in front of the user, and that
 * there is nothing to show the moment it ends. Returns the way to hand a run over at once, for the moment the app
 * leaves the front, when there may be no frame left for the state to arrive in.
 *
 * Here rather than on the settings screen because a run outlives the screen that started it: the user is free to go
 * back to their songs, or leave the app entirely, and the notification has to follow the run rather than the screen.
 * The strings are resolved here too, so that the notification is in the language chosen inside the app rather than
 * the system's.
 */
@Composable
internal fun rememberSyncNotifications(viewModel: CampfireViewModel): (SyncProgress) -> Unit {
    val syncNotifier = LocalSyncNotifier.current
    // Whether a run is going rather than how far it has got: the shells keep the count moving on their own (see
    // SyncNotification), so a notification handed over for every file a run moves would be work on the main thread
    // for nothing. The count of the moment is read as the notification is handed over, which is what the Android
    // service starts with.
    val isRunning by remember(viewModel) {
        viewModel.syncController.syncState.map { it.progress != null }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(viewModel.syncController.syncState.value.progress != null)
    val channelName = stringResource(Res.string.settings_sync_notification_channel)
    val title = stringResource(Res.string.settings_sync_notification_title)
    val stopLabel = stringResource(Res.string.settings_sync_cancel)
    val preparing = stringResource(Res.string.settings_sync_preparing)
    val progressBodyFormat = stringResource(Res.string.settings_sync_progress)
    // "Nothing to show" is only ever said after something was shown from here. Said on the first frame of every
    // composition, it would reach the Android shell as "stop the service" whenever the app was opened onto a run
    // that was already going in the background, and the service takes that as the user's request to stop the run.
    var hasShownNotification by remember { mutableStateOf(false) }
    val show = remember(syncNotifier, channelName, title, stopLabel, preparing, progressBodyFormat) {
        { progress: SyncProgress ->
            hasShownNotification = true
            syncNotifier.onSyncNotificationChanged(
                SyncNotification(
                    channelName = channelName,
                    title = title,
                    preparingBody = preparing,
                    progressBodyFormat = progressBodyFormat,
                    stopLabel = stopLabel,
                    progress = progress,
                ),
            )
        }
    }
    LaunchedEffect(isRunning, show) {
        val progress = viewModel.syncController.syncState.value.progress
        if (isRunning && progress != null) {
            show(progress)
        } else if (hasShownNotification) {
            hasShownNotification = false
            syncNotifier.onSyncNotificationChanged(null)
        }
    }
    return show
}

private val SyncState.progress get() = (this as? SyncState.Connected)?.progress
