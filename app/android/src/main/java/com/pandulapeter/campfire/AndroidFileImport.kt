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
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
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

/**
 * Reads [uris] off the main thread and queues what could be read. The application context is enough to read
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

/**
 * Text that was shared to Campfire rather than a file - a chord sheet selected on a web page, a note - which is
 * a song that has no file yet. It is given a name and sent down the same way a file is, so everything the import
 * does for a file it does for this: the song is named by its own header, a text the library already holds is
 * disregarded, and a name that is taken is asked about.
 *
 * @param subject What the sender called it, which stands in as the file name and so titles the song where the
 *   text declares no `{title}`.
 */
internal fun importSharedTexts(texts: List<String>, subject: String?) {
    importScope.launch {
        // The user picked Campfire in a share sheet, so a share with nothing in it is still answered: an empty
        // file is one the import reports as skipped.
        val files = texts.ifEmpty { listOf("") }.mapIndexed { index, text ->
            val name = subject?.cleanedForFileName()?.let { if (texts.size > 1) "$it ${index + 1}" else it } ?: text.firstPlainLine()
            ImportedFile(
                name = name.orEmpty().ifEmpty { UNTITLED } + LibraryFiles.SONG_EXTENSION,
                // A link by itself is not a song. Importing it as one would leave a file to find and delete, and
                // fetching what it points at is not something this app does.
                bytes = if (text.isOnlyLinks()) ByteArray(0) else text.encodeToByteArray(),
            )
        }
        pendingImports.send(files)
    }
}

private fun String.isOnlyLinks() = lineSequence().filter { it.isNotBlank() }.let { lines -> lines.any() && lines.all { LINK.matches(it.trim()) } }

/** Where a pasted chord sheet has its title: the first line that is neither a directive, a section, nor a comment. */
private fun String.firstPlainLine() = lineSequence()
    .map { it.trim() }
    .firstOrNull { it.isNotEmpty() && it.first() !in "{[#" }
    ?.cleanedForFileName()

/** Only what would break a file name; the library normalizes the rest on the way in. Null when nothing is left. */
private fun String.cleanedForFileName() = map { if (it == '/' || it == '\\' || it.isISOControl()) ' ' else it }
    .joinToString("")
    .trim()
    .take(MAX_SHARED_NAME_LENGTH)
    .trim()
    .ifEmpty { null }

private val LINK = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
private const val UNTITLED = "untitled"
private const val MAX_SHARED_NAME_LENGTH = 80
