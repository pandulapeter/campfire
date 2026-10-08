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

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.time.Instant

/**
 * An installed desktop build is started from Finder, the Start menu or a `.desktop` entry, where standard output goes
 * nowhere, and Campfire has no crash reporting by design. So everything the process prints - the diagnostics of the
 * shell and of the shared code, which are plain `println`s, Compose's own error output, and every exception nobody
 * caught - is also written to `campfire.log` in the data directory, next to `instance.lock` and outside `library/`, so
 * that it is never exported or synced. That file is what a user can attach to a bug report.
 *
 * It is bounded: once it reaches [MAX_BYTES] it becomes `campfire.log.1`, replacing the one before, and a new one is
 * started, so a session that runs for weeks holds at most two of them. Nothing here may ever throw at the app: a log
 * that cannot be written falls back to the console it was mirroring.
 */
internal object DesktopLog {

    /**
     * Called by the process that owns the data directory only, after the single-instance check: a second process that
     * hands its files over and exits would otherwise rotate the running one's log from under it.
     */
    fun install(directory: File) {
        val originalOut = System.out
        val originalErr = System.err
        try {
            val log = RollingLog(directory = directory, fallback = originalErr)
            System.setOut(mirrored(console = originalOut, log = log))
            System.setErr(mirrored(console = originalErr, log = log))
        } catch (exception: Exception) {
            originalErr.println("Could not open the log file: ${exception.message}")
        }
        // Since Java 7 AWT hands the exceptions of its event thread to the default handler too, and an exception a
        // coroutine lets through (viewModelScope has no handler of its own) ends up there as well.
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                System.err.println("Uncaught exception on ${thread.name}")
                throwable.printStackTrace(System.err)
            } catch (_: Throwable) {
                originalErr.println("Could not log an uncaught exception on ${thread.name}")
            }
        }
    }

    private fun mirrored(console: PrintStream, log: RollingLog) = PrintStream(
        object : OutputStream() {
            override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)

            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                console.write(bytes, offset, length)
                log.append(bytes, offset, length)
            }

            override fun flush() = console.flush()
        },
        true,
        Charsets.UTF_8,
    )

    /** Every line starts with the time it was written at, which is what lines a log up with what the user remembers. */
    private class RollingLog(
        directory: File,
        private val fallback: PrintStream,
    ) {
        private val file = File(directory, "campfire.log")
        private val previousFile = File(directory, "campfire.log.1")
        private var stream: OutputStream? = null
        private var size = 0L
        private var isAtLineStart = true

        init {
            directory.mkdirs()
            size = file.length()
            if (size >= MAX_BYTES) rotate() else stream = BufferedOutputStream(FileOutputStream(file, true))
        }

        @Synchronized
        fun append(bytes: ByteArray, offset: Int, length: Int) {
            if (stream == null) return
            try {
                for (index in offset until offset + length) {
                    if (isAtLineStart) {
                        val prefix = "[${Instant.now()}] ".encodeToByteArray()
                        if (size + prefix.size >= MAX_BYTES) rotate()
                        stream?.write(prefix)
                        size += prefix.size
                    }
                    stream?.write(bytes[index].toInt())
                    size++
                    isAtLineStart = bytes[index] == NEW_LINE
                }
                stream?.flush()
            } catch (exception: Exception) {
                // Given up on for the rest of the session rather than retried on every line: a full disk or a file
                // somebody locked would otherwise print this once for every line the app writes.
                runCatching { stream?.close() }
                stream = null
                fallback.println("Could not write the log file: ${exception.message}")
            }
        }

        /** Only ever at the start of a line, so that no line is split between the two files. */
        private fun rotate() {
            stream?.close()
            if (previousFile.exists() && !previousFile.delete()) error("Could not delete ${previousFile.name}")
            if (file.exists() && !file.renameTo(previousFile)) error("Could not rename ${file.name}")
            stream = BufferedOutputStream(FileOutputStream(file))
            size = 0
        }
    }

    private const val MAX_BYTES = 1024L * 1024
    private const val NEW_LINE = '\n'.code.toByte()
}
