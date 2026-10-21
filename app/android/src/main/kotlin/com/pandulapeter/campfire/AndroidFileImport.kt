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
import android.content.Intent
import android.net.Uri
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.share.SharedLink
import com.pandulapeter.campfire.presentation.ui.platform.SharedTextPayload
import com.pandulapeter.campfire.presentation.ui.platform.toImportedFiles
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

/** Texts shared to Campfire, kept apart from the files because one text is a song to make rather than a file to import. */
private val pendingSharedTexts = Channel<SharedTextPayload>(Channel.BUFFERED)

internal val sharedTextsToImport = pendingSharedTexts.receiveAsFlow()

/** Campfire links the app was opened with, kept with the files for the same reason: an intent is acted on once. */
private val pendingShareLinks = Channel<SharedLink>(Channel.BUFFERED)

internal val shareLinksToImport = pendingShareLinks.receiveAsFlow()

/**
 * Reads [uris] off the main thread and queues them, one that could not be read as an empty file that the import
 * reports as skipped. The application context is enough to read
 * them with, since the permission an intent grants is held by the app for as long as the activity record lives,
 * not by the activity instance that received it.
 */
internal fun Context.importFiles(uris: List<Uri>) {
    val context = applicationContext
    importScope.launch {
        val files = uris.toImportedFiles(context)
        if (files.isNotEmpty()) {
            pendingImports.send(files)
        }
    }
}

/** Queues texts shared to Campfire, for `PastedSongController.importSharedText` to decide what they come to. */
internal fun importSharedTexts(texts: List<String>, subject: String?) {
    importScope.launch { pendingSharedTexts.send(SharedTextPayload(texts = texts, subject = subject)) }
}

/** Queues a Campfire link the app was opened with, see `ImportController.importSharedLink`. */
internal fun importShareLink(link: SharedLink) {
    importScope.launch { pendingShareLinks.send(link) }
}

/**
 * `EXTRA_TEXT` is one text for a `SEND` and a list of them for a `SEND_MULTIPLE`, asked for in that order
 * because the platform logs a warning for an extra asked for as the wrong type. Some senders fill in only the
 * clip data.
 */
internal fun Intent.sharedTexts(): List<String> {
    val single = { getCharSequenceExtra(Intent.EXTRA_TEXT)?.let { listOf(it) } }
    val several = { getCharSequenceArrayListExtra(Intent.EXTRA_TEXT) }
    val texts = if (action == Intent.ACTION_SEND) single() ?: several() else several() ?: single()
    return (texts ?: clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).text } })
        .orEmpty()
        .map { it.toString() }
}

@Suppress("DEPRECATION")
internal fun Intent.parcelableExtra(name: String): Uri? = getParcelableExtra(name)

@Suppress("DEPRECATION")
internal fun Intent.parcelableArrayListExtra(name: String): List<Uri>? = getParcelableArrayListExtra(name)
