/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.pandulapeter.campfire.presentation.ui.platform

import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.ReceivedArchive
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.Foundation.NSError
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.posix.fclose
import platform.posix.ferror
import platform.posix.fopen
import platform.posix.fread

/**
 * [url], an archive that arrived, received into the staging folder through this receiver, its copy starting at once in
 * the receiver's job rather than on any queue later files wait on (a file iCloud has not downloaded yet may take its
 * whole download).
 *
 * A copy the app owns ([isOwnedCopy]: a `Documents/Inbox` copy, a picker's `asCopy` file, the share extension's, a drop's
 * file moved out of its provider's hands) is moved into the staging folder, which costs no disk; one that is still
 * there once the job ends, because it was discarded or failed, is deleted then. Anything else is the user's own file,
 * opened in place: it is copied inside a coordinated read (which downloads an iCloud file and waits for a writer) and
 * the security-scoped access, both lasting as long as the copy, with C's `fread` a MiB at a time, never `NSFileHandle`,
 * whose failures are Objective-C exceptions Kotlin cannot catch.
 *
 * @param size What the file's attributes said, null where they said nothing; the room for it is reserved first.
 * @param close Called once as the job ends, however it ends, after the owned copy is deleted.
 */
fun ArchiveReceiver.receiveUrl(url: NSURL, name: String, size: Long?, isOwnedCopy: Boolean, close: () -> Unit = {}): ImportedFile =
    receive(
        name = name,
        announcedSize = size,
        close = {
            if (isOwnedCopy) url.path?.let { NSFileManager.defaultManager.removeItemAtPath(it, error = null) }
            close()
        },
    ) { archive ->
        withContext(Dispatchers.IO) {
            if (isOwnedCopy) {
                val path = url.path ?: throw IllegalStateException("\"$name\" has no path.")
                archive.take(file = path, size = size ?: fileSizeAt(path) ?: 0L) { }
            } else {
                url.copyCoordinated(archive, currentCoroutineContext().job)
            }
        }
    }

/** The size the file system gives the file at [path], null where it gives none. */
fun fileSizeAt(path: String): Long? = (NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)?.get(NSFileSize) as? NSNumber)?.longLongValue

/**
 * The first [ImportLimits.SNIFF_LENGTH] bytes of the file at [path] (fewer only where it is shorter), read with C's
 * `fread`, which `ImportIntake.decide` is asked with; null where it cannot be opened or read.
 */
fun readHeadAt(path: String): ByteArray? {
    val file = fopen(path, "rb") ?: return null
    return try {
        val buffer = ByteArray(ImportLimits.SNIFF_LENGTH)
        val count = buffer.usePinned { fread(it.addressOf(0), 1.convert(), buffer.size.convert(), file).toInt() }
        if (count < buffer.size && ferror(file) != 0) null else buffer.copyOf(count)
    } finally {
        fclose(file)
    }
}

/**
 * Copies this file of the user's own into [archive] inside a coordinated read and its security-scoped access. Blocking,
 * so on [Dispatchers.IO]; the coordinator's accessor is a block nothing may throw out of, so a failure inside it is
 * carried out and thrown after it, and [job]'s cancellation is checked between reads.
 */
private fun NSURL.copyCoordinated(archive: ReceivedArchive, job: Job) {
    val isAccessible = startAccessingSecurityScopedResource()
    try {
        var failure: Throwable? = null
        var isCopied = false
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            NSFileCoordinator(filePresenter = null).coordinateReadingItemAtURL(url = this@copyCoordinated, options = 0uL, error = error.ptr) { coordinated ->
                // The coordinator may hand over a snapshot of a file being written, which is the app's only while this runs.
                try {
                    val path = (coordinated ?: this@copyCoordinated).path ?: throw IllegalStateException("The file has no path.")
                    copyFile(path, archive, job)
                    isCopied = true
                } catch (throwable: Throwable) {
                    failure = throwable
                }
            }
            error.value?.let { if (failure == null) failure = IllegalStateException("Could not read \"$this@copyCoordinated\": ${it.localizedDescription}") }
        }
        failure?.let { throw it }
        if (!isCopied) throw IllegalStateException("The coordinated read of \"$this\" never ran.")
    } finally {
        if (isAccessible) stopAccessingSecurityScopedResource()
    }
}

private fun copyFile(path: String, archive: ReceivedArchive, job: Job) {
    val file = fopen(path, "rb") ?: throw IllegalStateException("Could not open \"$path\".")
    try {
        val buffer = ByteArray(COPY_SLICE_SIZE)
        while (true) {
            job.ensureActive()
            val count = buffer.usePinned { fread(it.addressOf(0), 1.convert(), buffer.size.convert(), file).toInt() }
            if (count > 0) runBlocking { archive.write(buffer, 0, count) }
            if (count < buffer.size) {
                if (ferror(file) != 0) throw IllegalStateException("Could not read \"$path\".")
                break
            }
        }
    } finally {
        fclose(file)
    }
}

/** How much of a file of the user's own is read and written at a time. */
private const val COPY_SLICE_SIZE = 1 shl 20
