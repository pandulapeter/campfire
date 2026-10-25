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

/**
 * Keeps everything the page writes to the console, see [readProcessLog], and every error nothing caught, in the site's
 * `localStorage`: `println` is `console.log` here, and the console is gone with the tab. `localStorage` rather than the
 * OPFS the library is in, since it is written synchronously from the console call itself and outlives a reload. What is
 * written is stored a second later,
 * once for a burst of lines, and when the page is hidden. Tabs share it, the last one to store winning, which only
 * costs a log the lines of a tab that was not the one used.
 *
 * Called once, before anything else of the app's runs.
 */
fun installProcessLog() {
    if (installedWriter != null) return
    val writer = ProcessLogWriter(
        store = LocalStorageProcessLogStore(),
        maxBytes = PROCESS_LOG_MAX_BYTES,
        onFailure = ::logToConsoleOnly,
    )
    installedWriter = writer
    captureConsole { text -> writer.append(text.encodeToByteArray()) }
}

private var installedWriter: ProcessLogWriter? = null

internal actual fun readProcessLog(): String? = installedWriter?.read()?.decodeToString()

internal actual fun hasPendingCrashReport() = false

internal actual fun forgetPendingCrashReport() = Unit

private class LocalStorageProcessLogStore : ProcessLogStore {
    private var current = StringBuilder(loadStored(CURRENT_KEY).orEmpty())
    private var previous = loadStored(PREVIOUS_KEY).orEmpty()
    private var isPreviousStored = true
    private var isStoreScheduled = false
    private var hasReportedStoreFailure = false

    override val currentSize get() = current.toString().encodeToByteArray().size.toLong()

    override fun append(bytes: ByteArray, offset: Int, length: Int) {
        current.append(bytes.decodeToString(offset, offset + length))
        scheduleStore()
    }

    /** Nothing to hand over: what was appended is stored on its own schedule. */
    override fun flush() = Unit

    override fun rotate() {
        previous = current.toString()
        isPreviousStored = false
        current = StringBuilder()
        scheduleStore()
    }

    override fun read() = (previous + current).encodeToByteArray()

    private fun scheduleStore() {
        if (isStoreScheduled) return
        isStoreScheduled = true
        whenSettledOrHidden {
            isStoreScheduled = false
            // Not thrown at the writer, which would give the log up: storage that is full now may have room again later.
            // Said once, since every try comes a second after the last line written.
            // The previous file changes only at a rotation, and rewriting it, up to the whole limit, blocks the page.
            if (!isPreviousStored) isPreviousStored = store(PREVIOUS_KEY, previous)
            if (!isPreviousStored || !store(CURRENT_KEY, current.toString())) {
                if (!hasReportedStoreFailure) logToConsoleOnly("Could not store the log.")
                hasReportedStoreFailure = true
            }
        }
    }

    private companion object {
        const val CURRENT_KEY = "campfire-log"
        const val PREVIOUS_KEY = "campfire-log-previous"
    }
}

/**
 * Passes every console call, and every error and rejected promise nothing caught, on to [onText] as a line, after the
 * console has shown it. A console call made while [onText] runs is not passed on, so that nothing the log does to report
 * on itself is logged again, without end.
 */
private fun captureConsole(onText: (String) -> Unit): Unit = js(
    """{
        let isCapturing = false;
        const capture = (text) => {
            if (isCapturing) return;
            isCapturing = true;
            try {
                onText(text + '\n');
            } catch (e) {
            } finally {
                isCapturing = false;
            }
        };
        window.campfireConsoleWarn = console.warn.bind(console);
        const describe = (value) => value instanceof Error ? (value.stack || String(value)) : String(value);
        for (const level of ['log', 'info', 'warn', 'error', 'debug']) {
            const original = console[level].bind(console);
            console[level] = (...values) => {
                original(...values);
                capture(values.map(describe).join(' '));
            };
        }
        window.addEventListener('error', (event) => capture('Uncaught: ' + describe(event.error || event.message)));
        window.addEventListener('unhandledrejection', (event) => capture('Unhandled rejection: ' + describe(event.reason)));
    }""",
)

/** Calls [block] a second from now, or as soon as the page is hidden, whichever comes first, and only once. */
private fun whenSettledOrHidden(block: () -> Unit): Unit = js(
    """{
        let isDone = false;
        const run = () => {
            if (isDone) return;
            isDone = true;
            clearTimeout(timeout);
            window.removeEventListener('pagehide', run);
            document.removeEventListener('visibilitychange', onVisibilityChange);
            block();
        };
        const onVisibilityChange = () => { if (document.visibilityState === 'hidden') run(); };
        const timeout = setTimeout(run, 1000);
        window.addEventListener('pagehide', run);
        document.addEventListener('visibilitychange', onVisibilityChange);
    }""",
)

/** Null where nothing is stored, or storage is blocked for the site. */
private fun loadStored(key: String): String? = js(
    """{
        try {
            return window.localStorage.getItem(key);
        } catch (e) {
            return null;
        }
    }""",
)

/** False where storage is blocked for the site or full. */
private fun store(key: String, value: String): Boolean = js(
    """{
        try {
            window.localStorage.setItem(key, value);
            return true;
        } catch (e) {
            return false;
        }
    }""",
)

/** Through the console as it was before [captureConsole], which reports on the log without logging the report. */
private fun logToConsoleOnly(message: String): Unit = js("(window.campfireConsoleWarn || console.warn)(message)")
