/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.pandulapeter.campfire.presentation.ui.platform

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSLock
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSThread
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.posix.ENOENT
import platform.posix.F_OK
import platform.posix.FILE
import platform.posix.F_GETFL
import platform.posix.F_SETFL
import platform.posix.O_NONBLOCK
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.STDERR_FILENO
import platform.posix.STDOUT_FILENO
import platform.posix.access
import platform.posix.dup
import platform.posix.dup2
import platform.posix.errno
import platform.posix.fclose
import platform.posix.fcntl
import platform.posix.fflush
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.pipe
import platform.posix.read
import platform.posix.remove
import platform.posix.rename
import platform.posix.stat
import platform.posix.write
import kotlin.experimental.ExperimentalNativeApi

/**
 * Keeps everything the app prints, see [readProcessLog], in `campfire.log` in the caches directory, which is never
 * backed up; that the system may empty it when the device runs out of space costs nothing a log needs.
 *
 * Standard output and standard error, which `println` and `NSLog` write to, are pointed at a pipe that a thread of its own
 * reads, passing everything on to the console they went to before (Xcode's) and into the log. Its write end does not
 * block: should the reader ever fall behind, what the app prints is lost rather than the app stopped in the middle of a
 * `println`. An exception nobody caught is written into the log by the thread it ended, at once, since the process ends
 * before the reader would get to it, and leaves `campfire.crash` beside the log, see [hasPendingCrashReport].
 *
 * Called once, by the app's view controller, and never by the share extension, which is a process of its own.
 */
fun installProcessLog() {
    if (installedWriter != null) return
    val directory = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).firstOrNull() as? String ?: return
    val console = dup(STDERR_FILENO)
    val writer = try {
        ProcessLogWriter(
            store = PosixProcessLogStore("$directory/campfire.log"),
            maxBytes = PROCESS_LOG_MAX_BYTES,
            onFailure = { message -> console.writeText("$message\n") },
        )
    } catch (exception: Exception) {
        console.writeText("Could not open the log file: ${exception.message}\n")
        return
    }
    val readEnd = memScoped {
        val ends = allocArray<IntVar>(2)
        if (pipe(ends) != 0) return
        val writeEnd = ends[1]
        fcntl(writeEnd, F_SETFL, fcntl(writeEnd, F_GETFL) or O_NONBLOCK)
        dup2(writeEnd, STDOUT_FILENO)
        dup2(writeEnd, STDERR_FILENO)
        ends[0]
    }
    installedWriter = writer
    crashMarkerPath = "$directory/campfire.crash"
    isCrashPending = access("$directory/campfire.crash", F_OK) == 0
    NSThread { forward(readEnd = readEnd, console = console, writer = writer) }.start()
    var previousHook: ((Throwable) -> Unit)? = null
    previousHook = setUnhandledExceptionHook { throwable ->
        val text = "Uncaught exception: ${throwable.stackTraceToString()}\n"
        console.writeText(text)
        withLock { writer.append(text.encodeToByteArray()) }
        crashMarkerPath?.let { path -> fopen(path, "w")?.let(::fclose) }
        previousHook?.invoke(throwable)
    }
}

/** Held around the writer by the reader, by an uncaught exception, and by [readProcessLog]. */
private val lock = NSLock()

private var installedWriter: ProcessLogWriter? = null

private var crashMarkerPath: String? = null

private var isCrashPending = false

internal actual fun hasPendingCrashReport() = isCrashPending

internal actual fun forgetPendingCrashReport() {
    isCrashPending = false
    crashMarkerPath?.let { remove(it) }
}

internal actual fun readProcessLog(): String? {
    val writer = installedWriter ?: return null
    return withLock { writer.read() }?.decodeToString()
}

/**
 * Bounded, because the thread an uncaught exception ends may be the one holding it, and a log is never worth more than
 * the second it waits.
 */
private inline fun <T> withLock(block: () -> T): T? {
    if (!lock.lockBeforeDate(NSDate.dateWithTimeIntervalSinceNow(1.0))) return null
    return try {
        block()
    } finally {
        lock.unlock()
    }
}

/** Never returns while the pipe is open, and never throws: the thread it runs on is the log's for the whole process. */
private fun forward(readEnd: Int, console: Int, writer: ProcessLogWriter) {
    val buffer = ByteArray(4096)
    while (true) {
        val count = buffer.usePinned { read(readEnd, it.addressOf(0), buffer.size.convert()) }.toInt()
        if (count < 0) continue
        if (count == 0) return
        buffer.usePinned { write(console, it.addressOf(0), count.convert()) }
        withLock { writer.append(buffer, 0, count) }
    }
}

private fun Int.writeText(text: String) {
    val bytes = text.encodeToByteArray()
    if (bytes.isNotEmpty()) bytes.usePinned { write(this, it.addressOf(0), bytes.size.convert()) }
}

private class PosixProcessLogStore(private val path: String) : ProcessLogStore {
    private val previousPath = "$path.1"
    private var file = open(path, "a")

    override val currentSize: Long
        get() = memScoped {
            val info = alloc<stat>()
            if (stat(path, info.ptr) == 0) info.st_size else 0L
        }

    override fun append(bytes: ByteArray, offset: Int, length: Int) {
        if (length == 0) return
        val written = bytes.usePinned { fwrite(it.addressOf(offset), 1u, length.convert(), file) }
        if (written.toInt() != length || fflush(file) != 0) error("Could not write $path (errno $errno)")
    }

    override fun rotate() {
        fclose(file)
        remove(previousPath)
        if (rename(path, previousPath) != 0 && errno != ENOENT) error("Could not rename $path (errno $errno)")
        file = open(path, "w")
    }

    override fun read(): ByteArray {
        fflush(file)
        return readBytes(previousPath) + readBytes(path)
    }

    private fun open(path: String, mode: String): CPointer<FILE> = fopen(path, mode) ?: error("Could not open $path (errno $errno)")

    private fun readBytes(path: String): ByteArray {
        val file = fopen(path, "r") ?: return ByteArray(0)
        try {
            fseek(file, 0, SEEK_END)
            val size = ftell(file).toInt()
            fseek(file, 0, SEEK_SET)
            if (size <= 0) return ByteArray(0)
            val bytes = ByteArray(size)
            val count = bytes.usePinned { fread(it.addressOf(0), 1u, size.convert(), file) }
            return bytes.copyOf(count.toInt())
        } finally {
            fclose(file)
        }
    }
}
