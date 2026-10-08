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

import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.presentation.ui.platform.readAsImportedFiles
import java.awt.Desktop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Every file the operating system asks Campfire to open, whichever way the request arrives: as a command line
 * argument, which is how Windows and Linux start an application for a file, or as the Apple event macOS sends
 * instead - to an application that is starting and to one that is already running alike. [open] is the one way in,
 * so whatever else learns of a file to open (a second instance handing its arguments to this one) calls it too.
 *
 * A channel rather than a state: a request is imported once, by whoever collects [files], and it waits there for
 * as long as nobody does - the first requests arrive before the window exists.
 */
internal object OpenedFiles {

    private val requests = Channel<List<String>>(Channel.UNLIMITED)

    /** The requested files that could be read, one list per request, read off the thread that collects them. */
    val files: Flow<List<ImportedFile>> = requests.receiveAsFlow().map { it.readAsImportedFiles() }.flowOn(Dispatchers.IO)

    /** Safe to call from any thread, and before the window exists. */
    fun open(paths: List<String>) {
        if (paths.isNotEmpty()) requests.trySend(paths)
    }

    /**
     * Only macOS sends these events, and only there is this worth the price: asking `Desktop` anything starts the
     * AWT toolkit, which on Linux reads the display scale before Compose has had the chance to set it.
     *
     * The handler is never taken away again. The JDK holds on to the events that arrive before the first handler is
     * registered - the file the app was started for - but drops every one that finds the handler gone after that.
     */
    fun listenForSystemRequests() {
        if ("mac" !in System.getProperty("os.name").lowercase()) return
        val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop().takeIf { it.isSupported(Desktop.Action.APP_OPEN_FILE) } else null
        desktop?.setOpenFileHandler { event -> open(event.files.map { it.absolutePath }) }
    }
}
