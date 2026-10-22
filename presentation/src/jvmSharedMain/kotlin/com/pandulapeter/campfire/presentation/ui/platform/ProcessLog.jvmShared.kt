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

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream

/**
 * Mirrors everything the process prints, on standard output and standard error, into `campfire.log` in [directory], see
 * [readProcessLog], and every exception nobody caught with it. Since Java 7 AWT hands the exceptions of its event thread
 * to the default handler too, and an exception a coroutine lets through (viewModelScope has no handler of its own) ends up
 * there as well. The handler that was there before is still called after the exception is written down, which on
 * Android is what ends the process and reports the crash. Such an exception also leaves `campfire.crash` beside the log,
 * see [hasPendingCrashReport].
 *
 * Called once, as early as the shell can: the desktop's process that owns the data directory only, after the
 * single-instance check, since a second process that hands its files over and exits would otherwise rotate the running
 * one's log from under it.
 */
fun installProcessLog(directory: File) {
    val originalOut = System.out
    val originalErr = System.err
    try {
        directory.mkdirs()
        val writer = ProcessLogWriter(
            store = FileProcessLogStore(directory),
            maxBytes = PROCESS_LOG_MAX_BYTES,
            onFailure = originalErr::println,
        )
        installedWriter = writer
        System.setOut(mirrored(console = originalOut, writer = writer))
        System.setErr(mirrored(console = originalErr, writer = writer))
    } catch (exception: Exception) {
        originalErr.println("Could not open the log file: ${exception.message}")
    }
    val marker = File(directory, CRASH_MARKER_NAME)
    crashMarker = marker
    isCrashPending = marker.exists()
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        try {
            marker.createNewFile()
            System.err.println("Uncaught exception on ${thread.name}")
            throwable.printStackTrace(System.err)
        } catch (_: Throwable) {
            originalErr.println("Could not log an uncaught exception on ${thread.name}")
        }
        previousHandler?.uncaughtException(thread, throwable)
    }
}

/** Held by the streams and by [readProcessLog], which reads from another thread than the ones printing. */
private val lock = Any()

@Volatile
private var installedWriter: ProcessLogWriter? = null

private var crashMarker: File? = null

@Volatile
private var isCrashPending = false

internal actual fun hasPendingCrashReport() = isCrashPending

internal actual fun forgetPendingCrashReport() {
    isCrashPending = false
    try {
        crashMarker?.delete()
    } catch (exception: Exception) {
        println("Could not forget the crash: ${exception.message}")
    }
}

private const val CRASH_MARKER_NAME = "campfire.crash"

internal actual fun readProcessLog(): String? {
    val writer = installedWriter ?: return null
    val bytes = synchronized(lock) { writer.read() } ?: return null
    return bytes.decodeToString().withoutUserName(home = System.getProperty("user.home"), userName = System.getProperty("user.name"))
}

/**
 * The log without the name the user logs in to the computer with, which a desktop's paths are full of: the home
 * directory becomes `~`, and the name where it is a whole segment of any other path (a file opened from another disk, a
 * sandboxed app whose home is its container) becomes `<user>`. A phone's paths name no one.
 */
internal fun String.withoutUserName(home: String?, userName: String?): String {
    var text = this
    if (home != null && home.length > 1) text = text.replace(home.trimEnd('/', '\\'), "~")
    if (!userName.isNullOrBlank()) {
        text = text.replace(Regex("(?<=[/\\\\])${Regex.escape(userName)}(?=[/\\\\]|$)", RegexOption.MULTILINE), "<user>")
    }
    return text
}

private fun mirrored(console: PrintStream, writer: ProcessLogWriter) = PrintStream(
    object : OutputStream() {
        override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            console.write(bytes, offset, length)
            synchronized(lock) { writer.append(bytes, offset, length) }
        }

        override fun flush() = console.flush()
    },
    true,
    Charsets.UTF_8,
)

private class FileProcessLogStore(directory: File) : ProcessLogStore {
    private val file = File(directory, FILE_NAME)
    private val previousFile = File(directory, "$FILE_NAME.1")
    private var stream: OutputStream = BufferedOutputStream(FileOutputStream(file, true))

    override val currentSize get() = file.length()

    override fun append(bytes: ByteArray, offset: Int, length: Int) {
        stream.write(bytes, offset, length)
        stream.flush()
    }

    override fun rotate() {
        stream.close()
        if (previousFile.exists() && !previousFile.delete()) error("Could not delete ${previousFile.name}")
        if (file.exists() && !file.renameTo(previousFile)) error("Could not rename ${file.name}")
        stream = BufferedOutputStream(FileOutputStream(file))
    }

    override fun read() = previousFile.readBytesIfThere() + file.readBytesIfThere()

    private fun File.readBytesIfThere() = if (exists()) readBytes() else ByteArray(0)

    private companion object {
        const val FILE_NAME = "campfire.log"
    }
}
