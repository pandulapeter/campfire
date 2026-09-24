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
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.pandulapeter.campfire.presentation.ui.components.isAnyOverflowMenuOpen
import com.pandulapeter.campfire.presentation.ui.platform.DesktopFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.openUrl
import com.pandulapeter.campfire.presentation.ui.platform.readAsImportedFiles
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import java.awt.datatransfer.DataFlavor
import java.io.File

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
                            // The transferable is the drag source's, and converting it can fail however it was offered
                            // (the JDK throws for a malformed uri-list on X11, or a source that is gone); an exception out
                            // of here would also skip the end of the session, and the window would refuse every drag after
                            // this one.
                            val files = try {
                                event.awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
                            } catch (exception: Exception) {
                                println("Could not read the dropped files: ${exception.message}")
                                return false
                            }
                            val paths = files.orEmpty().filterIsInstance<File>().map { it.absolutePath }
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
 * To be wired into the window's key event handler. Returns true if the event was consumed. Ctrl / Cmd + F opens the
 * search of the list screen that is on top ([CampfireViewModel.openCurrentSearch]); it is answered here because this
 * handler hears the keys that nothing focused in the window took, and nothing is focused on a list screen until its
 * search is. Ctrl / Cmd + plus, minus and zero change the text size of the song details screen
 * ([CampfireViewModel.zoomSongText]), the shortcuts a browser zooms a page with.
 *
 * @param onExit Closes the application, called when there is nothing left to navigate back from, and only once a save
 *   that is still being written has finished, see [CampfireViewModel.requestExit].
 */
fun CampfireViewModel.handleKeyEvent(keyEvent: KeyEvent, onExit: () -> Unit): Boolean {
    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.F && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && !keyEvent.isAltPressed) {
        return openCurrentSearch()
    }
    // Alt is left out because AltGr arrives as Ctrl + Alt on Windows, and AltGr with these keys types a character on
    // some layouts. Shift is not: the plus of a US layout is Shift + equals.
    if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && !keyEvent.isAltPressed) {
        val steps = when (keyEvent.key) {
            Key.Equals, Key.Plus, Key.NumPadAdd -> 1
            Key.Minus, Key.NumPadSubtract -> -1
            Key.Zero, Key.NumPad0 -> null
            else -> return false
        }
        return zoomSongText(steps)
    }
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
