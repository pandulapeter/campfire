/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.resources.import_notification_channel
import com.pandulapeter.campfire.presentation.resources.import_progress_bytes_downloading
import com.pandulapeter.campfire.presentation.resources.import_progress_bytes_downloading_so_far
import com.pandulapeter.campfire.presentation.resources.import_progress_bytes_receiving
import com.pandulapeter.campfire.presentation.resources.import_progress_bytes_receiving_so_far
import com.pandulapeter.campfire.presentation.resources.import_progress_bytes_unpacking
import com.pandulapeter.campfire.presentation.resources.import_progress_bytes_unpacking_so_far
import com.pandulapeter.campfire.presentation.resources.import_progress_downloading
import com.pandulapeter.campfire.presentation.resources.import_progress_importing
import com.pandulapeter.campfire.presentation.resources.import_progress_receiving
import com.pandulapeter.campfire.presentation.resources.import_progress_title
import com.pandulapeter.campfire.presentation.resources.import_progress_unpacking
import com.pandulapeter.campfire.presentation.resources.settings_library_size_bytes
import com.pandulapeter.campfire.presentation.resources.settings_library_size_decimal_separator
import com.pandulapeter.campfire.presentation.resources.settings_library_size_gigabytes
import com.pandulapeter.campfire.presentation.resources.settings_library_size_kilobytes
import com.pandulapeter.campfire.presentation.resources.settings_library_size_megabytes
import com.pandulapeter.campfire.presentation.ui.platform.ImportNotification
import com.pandulapeter.campfire.presentation.ui.platform.LocalImportNotifier
import com.pandulapeter.campfire.presentation.ui.state.importing.COMPLETED
import com.pandulapeter.campfire.presentation.ui.state.importing.SIZE
import com.pandulapeter.campfire.presentation.ui.state.importing.TOTAL

/**
 * Hands the platform shell the words of an import's notification, in the language chosen in the app, on the first
 * frame and whenever they change: the shells watch the import's work themselves (`ImportWork`) and only need words to
 * write it in. The sentences are the import screen's own, resolved with placeholders the shells fill in
 * (`ImportNotificationText.kt`), since the notification moves on after the composition has stopped.
 */
@Composable
internal fun ImportNotificationEffect() {
    val importNotifier = LocalImportNotifier.current
    val notification = ImportNotification(
        channelName = stringResource(Res.string.import_notification_channel),
        title = stringResource(Res.string.import_progress_title),
        receiving = ImportNotification.Phase(
            line = stringResource(Res.string.import_progress_receiving),
            bytesOfTotal = stringResource(Res.string.import_progress_bytes_receiving, COMPLETED, TOTAL),
            bytesSoFar = stringResource(Res.string.import_progress_bytes_receiving_so_far, COMPLETED),
        ),
        downloading = ImportNotification.Phase(
            line = stringResource(Res.string.import_progress_downloading),
            bytesOfTotal = stringResource(Res.string.import_progress_bytes_downloading, COMPLETED, TOTAL),
            bytesSoFar = stringResource(Res.string.import_progress_bytes_downloading_so_far, COMPLETED),
        ),
        unpacking = ImportNotification.Phase(
            line = stringResource(Res.string.import_progress_unpacking),
            bytesOfTotal = stringResource(Res.string.import_progress_bytes_unpacking, COMPLETED, TOTAL),
            bytesSoFar = stringResource(Res.string.import_progress_bytes_unpacking_so_far, COMPLETED),
        ),
        writing = stringResource(Res.string.import_progress_importing),
        sizeUnits = listOf(
            stringResource(Res.string.settings_library_size_bytes, SIZE),
            stringResource(Res.string.settings_library_size_kilobytes, SIZE),
            stringResource(Res.string.settings_library_size_megabytes, SIZE),
            stringResource(Res.string.settings_library_size_gigabytes, SIZE),
        ),
        decimalSeparator = stringResource(Res.string.settings_library_size_decimal_separator),
        cancelLabel = stringResource(Res.string.cancel),
    )
    LaunchedEffect(importNotifier, notification) { importNotifier.onImportNotificationChanged(notification) }
}
