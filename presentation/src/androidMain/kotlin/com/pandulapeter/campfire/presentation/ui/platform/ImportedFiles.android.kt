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

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.pandulapeter.campfire.data.model.importing.ImportBudget
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import java.io.ByteArrayInputStream
import java.io.SequenceInputStream

/**
 * Reads the documents the system handed over - picked, opened with Campfire, shared to it or dropped onto it - within
 * one [ImportBudget]. One that cannot be read is handed over empty, which the import reports as skipped: the user asked
 * for it, and leaving it out would answer them with nothing at all - while one bad file still does not lose the ones
 * next to it.
 *
 * With a [receiver], see [takeImportedFile]. Pictures picked for a song pass none.
 */
fun List<Uri>.toImportedFiles(context: Context, receiver: ArchiveReceiver? = archiveReceiver()): List<ImportedFile> {
    val budget = ImportBudget()
    return map { uri ->
        // The query that knows the display name can be what fails, so it is asked on its own.
        val (displayName, size) = try {
            uri.nameAndSize(context)
        } catch (exception: Exception) {
            uri.fallbackName to null
        }
        val name = pictureFileName(displayName, runCatching { context.contentResolver.getType(uri) }.getOrNull())
        try {
            if (receiver != null) {
                context.takeImportedFile(uri = uri, name = name, size = size, budget = budget, receiver = receiver)
            } else {
                budget.read(name = name, size = size) { limit ->
                    // The size is the provider's own claim and is missing as often as not, so the read stops by itself.
                    context.contentResolver.openInputStream(uri)?.use { it.readAtMost(limit = limit.toInt() + 1, size = size) }
                }
            }
        } catch (exception: Exception) {
            println("Could not read \"$uri\": ${exception.message}")
            null
        } ?: ImportedFile.unread(name)
    }
}

/**
 * [uri] opened once, its first bytes peeked at, and then read whole as [ImportBudget.read] reads a file, or, where it is
 * an archive, handed to [receiver], whose copy starts at once from the stream already open: an activity's grant ends with
 * it, but a stream opened under the grant reads to its end while the process lives.
 *
 * Opened through an unstable provider client, never `openInputStream`: the stable reference that holds would take
 * Campfire down with a provider process that dies, even in front, where an unstable one survives it, its pipe then read
 * as a clean end that the copy's size check tells from the file's. A `file:` address has no provider to hold.
 */
private fun Context.takeImportedFile(uri: Uri, name: String, size: Long?, budget: ImportBudget, receiver: ArchiveReceiver): ImportedFile? {
    val client = if (uri.scheme == ContentResolver.SCHEME_CONTENT) contentResolver.acquireUnstableContentProviderClient(uri) ?: return null else null
    val input = try {
        if (client != null) client.openFile(uri, "r")?.let(ParcelFileDescriptor::AutoCloseInputStream) else contentResolver.openInputStream(uri)
    } catch (exception: Exception) {
        client?.close()
        throw exception
    }
    val close: () -> Unit = {
        try {
            input?.close()
        } finally {
            client?.close()
        }
    }
    if (input == null) {
        close()
        return null
    }
    var isReceived = false
    try {
        val head = input.readHead()
        return budget.take(
            name = name,
            size = size,
            head = head,
            readBytes = { limit -> SequenceInputStream(ByteArrayInputStream(head), input).readAtMost(limit = limit.toInt() + 1, size = size) },
            stage = {
                isReceived = true
                receiver.receiveStream(name = name, size = size, head = head, input = input, close = close)
            },
        )
    } finally {
        if (!isReceived) close()
    }
}

/** The extension is what the import goes by, and for a content URI only the display name carries it. */
internal fun Uri.nameAndSize(context: Context): Pair<String, Long?> = context.contentResolver
    .query(this, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
    ?.use { cursor ->
        if (cursor.moveToFirst()) (cursor.getString(0) ?: fallbackName) to (if (cursor.isNull(1)) null else cursor.getLong(1)) else null
    }
    ?: (fallbackName to null)

internal val Uri.fallbackName get() = lastPathSegment.orEmpty().substringAfterLast('/')
