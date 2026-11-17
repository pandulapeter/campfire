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

import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.ReceivedArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

/**
 * Copies the stream, front to back, into [archive], a slice at a time on [Dispatchers.IO], checking for cancellation
 * between slices: how Android and the desktop receive an archive that arrived into the staging folder. A stream that ends
 * before [expectedSize], where the platform said one, is a file that could not be read whole: a content provider's pipe
 * whose process died reads as a clean end, which only the size it gave away tells from the file's. Without one, a
 * truncated copy is refused by the zip reader, which finds no end record. Longer than said is taken, as a download's body
 * is. Leaves the stream open, its caller's to close.
 */
internal suspend fun InputStream.copyInto(archive: ReceivedArchive, expectedSize: Long?) = withContext(Dispatchers.IO) {
    val buffer = ByteArray(ARCHIVE_COPY_SLICE_SIZE)
    var copied = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = read(buffer)
        if (count < 0) break
        if (count == 0) continue
        archive.write(buffer, 0, count)
        copied += count
    }
    if (expectedSize != null && copied < expectedSize) throw IOException("Only $copied of the $expectedSize bytes announced could be read.")
}

/**
 * The first [ImportLimits.SNIFF_LENGTH] bytes of the stream, fewer only where it is shorter, which is what
 * `ImportIntake.decide` is asked with. Read by hand rather than with `readNBytes(Int)`, which Android's `InputStream` does not have.
 */
internal fun InputStream.readHead(): ByteArray {
    val head = ByteArray(ImportLimits.SNIFF_LENGTH)
    var filled = 0
    while (filled < head.size) {
        val count = read(head, filled, head.size - filled)
        if (count < 0) break
        filled += count
    }
    return if (filled == head.size) head else head.copyOf(filled)
}

/**
 * [name], [size] bytes long where the platform says, opened once as [input] and its [head] already read: received into
 * the staging folder through this receiver from the stream as it is, its copy starting at once and the stream let go
 * of by [close] as the copy ends. The stream is the receiver's from here on.
 */
internal fun ArchiveReceiver.receiveStream(
    name: String,
    size: Long?,
    head: ByteArray,
    input: InputStream,
    close: () -> Unit = input::close,
): ImportedFile =
    receive(name = name, announcedSize = size, close = close) { archive ->
        if (head.isNotEmpty()) archive.write(head, 0, head.size)
        input.copyInto(archive, expectedSize = size?.minus(head.size))
    }

/** How much of an archive is read and written at a time: few enough calls into the staging, and a prompt cancel. */
private const val ARCHIVE_COPY_SLICE_SIZE = 256 * 1024
