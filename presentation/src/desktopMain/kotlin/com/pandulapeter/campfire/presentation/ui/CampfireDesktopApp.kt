/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.pandulapeter.campfire.presentation.ui.components.isAnyOverflowMenuOpen
import com.pandulapeter.campfire.presentation.ui.platform.DesktopFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.readAsImportedFiles
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import java.awt.Desktop
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.net.URI

/**
 * Desktop shell of the shared UI. Desktop has no back gesture, so the Escape key (see [handleKeyEvent]) dismisses
 * whatever is open on top of the app - a dialog, a bottom sheet or an overflow menu - pops the back stack when there
 * is none, clears the Songs search query on the root screen if it's not already empty, and closes the application
 * otherwise.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CampfireDesktopApp(
    viewModel: CampfireViewModel = koinViewModel(),
    filesToImport: Flow<List<ImportedFile>> = emptyFlow(),
) = CompositionLocalProvider(
    LocalFilePicker provides DesktopFilePicker
) {
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Files dropped anywhere on the window are imported, which is the shortest path there is from a folder
            // of songs to a library.
            .dragAndDropTarget(
                shouldStartDragAndDrop = { it.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) },
                target = remember {
                    object : DragAndDropTarget {
                        override fun onDrop(event: DragAndDropEvent): Boolean {
                            val paths = (event.awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                                .orEmpty()
                                .filterIsInstance<File>()
                                .map { it.absolutePath }
                            // Only the paths are taken here: this is the AWT event thread, and the files are read off it.
                            scope.launch { viewModel.importFiles(withContext(Dispatchers.IO) { paths.readAsImportedFiles() }) }
                            return true
                        }
                    }
                },
            )
    ) {
        CampfireApp(
            viewModel = viewModel,
            urlOpener = { url -> if (!openUrl(url)) viewModel.onLinkNotOpened(url) },
            filesToImport = filesToImport,
        )
    }
}


/**
 * To be wired into the window's key event handler. Returns true if the event was consumed.
 *
 * @param onExit Closes the application, called when there is nothing left to navigate back from, and only once a save
 *   that is still being written has finished, see [CampfireViewModel.requestExit].
 */
fun CampfireViewModel.handleKeyEvent(keyEvent: KeyEvent, onExit: () -> Unit): Boolean {
    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
        // Window key handlers run before Compose turns Escape into a back event, so consuming it here would pop the
        // back stack behind an open dialog, bottom sheet or overflow menu. Those register their own back handlers:
        // leaving the event unconsumed lets the top one dismiss itself (with its exit animation).
        if (visibleDialog.value != null || isAnyOverflowMenuOpen) return false
        // The search of whichever list screen is up comes before the back stack, and not only because closing it
        // is the smaller step: selecting a tab rebuilds the stack around it, so the setlists screen is reached with
        // the songs screen still under it and every Escape there would otherwise leave the tab with the search
        // still open behind it. It is closed from here rather than by the back handler the field registers, since
        // this handler sees the key first and would consume it either way.
        val search = currentSearch
        when {
            search?.isOpen?.value == true -> search.close()
            backStack.size > 1 -> navigateBack()
            else -> requestExit(onExit)
        }
        return true
    }
    return false
}

/**
 * To be wired into the window's preview key handler, which sees every key event before anything in the window does.
 * Swallows the Escapes a held key repeats: AWT sends one `KEY_PRESSED` per repeat with no release in between, and every
 * one of them would otherwise be another back - holding the key a moment too long closed every screen and then the
 * app, and in the editor it opened and dismissed the unsaved changes question over and over.
 *
 * A release is recognised however it reaches the window, so a press that was consumed by something else still ends.
 * Where the platform reports a repeat as a release and a press (X11 without detectable auto-repeat), a repeat looks
 * like a new press and nothing can be done about it here.
 */
fun handlePreviewKeyEvent(keyEvent: KeyEvent): Boolean {
    if (keyEvent.key != Key.Escape) return false
    return when (keyEvent.type) {
        KeyEventType.KeyDown -> isEscapeHeld.also { isEscapeHeld = true }
        KeyEventType.KeyUp -> false.also { isEscapeHeld = false }
        else -> false
    }
}

/**
 * Forgets a held Escape. A window that loses the focus while the key is down never gets its release, and without this
 * the first Escape after coming back would be taken for a repeat and swallowed.
 */
fun resetEscapeKey() {
    isEscapeHeld = false
}

/** One window, only ever touched on the AWT event thread, so a top-level flag is all the state [handlePreviewKeyEvent] needs. */
private var isEscapeHeld = false

/**
 * Opens [url] in the system's browser and answers whether anything took it. `java.awt.Desktop` goes first, and the
 * operating system's own command is what is left - both where AWT has no desktop to speak of, which is a Linux
 * session without the GNOME libraries it looks for, and where it has one that fails, as `browse` does on a machine
 * with no default browser registered.
 */
private fun openUrl(url: String) = openWithAwt(url) || openWithSystemCommand(url)

/** `getDesktop` throws where `isDesktopSupported` says no, so it is only asked for after that has said yes. */
private fun openWithAwt(url: String) = try {
    val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop().takeIf { it.isSupported(Desktop.Action.BROWSE) } else null
    desktop?.browse(URI(url))
    desktop != null
} catch (exception: Exception) {
    println("Could not open $url through java.awt.Desktop: ${exception.message}")
    false
} catch (error: LinkageError) {
    // The desktop peer loads native libraries the first time it is asked for, and one that does not link is an
    // Error rather than an Exception. This runs inside a click handler, where either would close the window.
    println("Could not open $url through java.awt.Desktop: ${error.message}")
    false
}

private fun openWithSystemCommand(url: String) = try {
    val osName = System.getProperty("os.name").orEmpty().lowercase()
    val command = when {
        "mac" in osName -> arrayOf("open", url)
        "win" in osName -> arrayOf("rundll32", "url.dll,FileProtocolHandler", url)
        else -> arrayOf("xdg-open", url)
    }
    // Discarded rather than piped: xdg-open may become the browser itself, which then writes its log into a pipe
    // nobody reads and stops once that is full.
    ProcessBuilder(*command)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
    true
} catch (exception: Exception) {
    println("Could not open $url: ${exception.message}")
    false
}
