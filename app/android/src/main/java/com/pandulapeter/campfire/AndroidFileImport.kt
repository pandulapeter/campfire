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

import android.content.Context
import android.net.Uri
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.presentation.ui.platform.toImportedFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Files the system handed over, on their way to the UI. Both the channel and the scope the files are read in
 * belong to the process rather than to the activity: an intent is acted on once, so a read that a rotation
 * cancelled, or a list left in the channel of an activity instance that is gone, would be an import the user
 * asked for and never got.
 */
private val pendingImports = Channel<List<ImportedFile>>(Channel.BUFFERED)

private val importScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

internal val filesToImport = pendingImports.receiveAsFlow()

/**
 * Reads [uris] off the main thread and queues what could be read. The application context is enough to read
 * them with, since the permission an intent grants is held by the app for as long as the activity record lives,
 * not by the activity instance that received it.
 */
internal fun Context.importFiles(uris: List<Uri>) {
    val context = applicationContext
    importScope.launch {
        val files = uris.mapNotNull { it.toImportedFile(context) }
        if (files.isNotEmpty()) {
            pendingImports.send(files)
        }
    }
}
