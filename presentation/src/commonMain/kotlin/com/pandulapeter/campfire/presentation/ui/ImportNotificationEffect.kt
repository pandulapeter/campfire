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
import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.resources.import_notification_channel
import com.pandulapeter.campfire.presentation.resources.import_progress_title
import com.pandulapeter.campfire.presentation.resources.settings_library_size_decimal_separator
import com.pandulapeter.campfire.presentation.ui.components.SIZE_UNITS
import com.pandulapeter.campfire.presentation.ui.dialogs.bytesOfTotalLabel
import com.pandulapeter.campfire.presentation.ui.dialogs.bytesSoFarLabel
import com.pandulapeter.campfire.presentation.ui.dialogs.label
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
        receiving = phaseWordsOf(ImportProgress.Phase.RECEIVING),
        downloading = phaseWordsOf(ImportProgress.Phase.DOWNLOADING),
        unpacking = phaseWordsOf(ImportProgress.Phase.UNPACKING),
        writing = stringResource(ImportProgress.Phase.IMPORTING.label),
        sizeUnits = SIZE_UNITS.map { stringResource(it, SIZE) },
        decimalSeparator = stringResource(Res.string.settings_library_size_decimal_separator),
        cancelLabel = stringResource(Res.string.cancel),
    )
    LaunchedEffect(importNotifier, notification) { importNotifier.onImportNotificationChanged(notification) }
}

/** The import screen's words for [phase], its sizes left as placeholders. */
@Composable
private fun phaseWordsOf(phase: ImportProgress.Phase) = ImportNotification.Phase(
    line = stringResource(phase.label),
    bytesOfTotal = stringResource(phase.bytesOfTotalLabel, COMPLETED, TOTAL),
    bytesSoFar = stringResource(phase.bytesSoFarLabel, COMPLETED),
)
