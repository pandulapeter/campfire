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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.EscapeStep
import com.pandulapeter.campfire.presentation.ui.navigation.escapeStepOf
import com.pandulapeter.campfire.presentation.ui.platform.DesktopFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.openUrl
import com.pandulapeter.campfire.presentation.ui.platform.readAsImportedFiles
import com.pandulapeter.campfire.presentation.ui.songLayout.rememberDefaultSectionLabels
import com.pandulapeter.campfire.presentation.ui.state.importing.isDropAvailable
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.share.SharedLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import java.awt.datatransfer.DataFlavor
import java.io.File

/**
 * Desktop shell of the shared UI. Desktop has no back gesture, so the Escape key (see [handleKeyEvent]) dismisses
 * whatever is open on top of the app - a dialog, a bottom sheet or an overflow menu - and otherwise leaves the setlist
 * reorder mode, closes the search of the list screen on top, or pops the back stack, in that order; with nothing left
 * to go back from it asks whether to close the application (`AppExitController.confirmExit`).
 *
 * @param shareLinks The `campfire://s/` links the app was started with or sent; see [CampfireApp].
 * @param onBackgroundColorChanged See [CampfireApp].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CampfireDesktopApp(
    viewModel: CampfireViewModel = koinViewModel(),
    filesToImport: Flow<List<ImportedFile>> = emptyFlow(),
    shareLinks: Flow<SharedLink> = emptyFlow(),
    onBackgroundColorChanged: (Color) -> Unit = {},
) = CompositionLocalProvider(
    LocalFilePicker provides DesktopFilePicker
) {
    val scope = rememberCoroutineScope()
    val sectionLabels = rememberDefaultSectionLabels()
    // After the library has been read, so that it never competes with the launch's own reads.
    LaunchedEffect(Unit) {
        viewModel.libraryState.isLoading.first { !it }
        warmUpSongView(viewModel.songRenderer, sectionLabels)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Files dropped anywhere on the window are imported, which is the shortest path there is from a folder
            // of songs to a library. Text dropped on the song list is a new song, as Paste from clipboard makes one; a
            // file list wins over a text, since a file dragged from a file manager offers both. Read only mode takes
            // neither.
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    val transferable = event.awtTransferable
                    viewModel.preferencesController.isDropAvailable && (
                        transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
                            transferable.isDataFlavorSupported(DataFlavor.stringFlavor) && viewModel.pastedSongController.isNewSongPasteAvailable
                        )
                },
                target = remember {
                    object : DragAndDropTarget {
                        override fun onDrop(event: DragAndDropEvent): Boolean {
                            // The transferable is the drag source's, and converting it can fail however it was offered
                            // (the JDK throws for a malformed uri-list on X11, or a source that is gone); an exception out
                            // of here would also skip the end of the session, and the window would refuse every drag after
                            // this one.
                            val transferable = event.awtTransferable
                            if (!viewModel.preferencesController.isDropAvailable) return false
                            val files = try {
                                if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                                    // Checked again rather than trusted from the start of the drag, during which a dialog
                                    // or the editor can have opened.
                                    if (!viewModel.pastedSongController.isNewSongPasteAvailable) return false
                                    val text = transferable.getTransferData(DataFlavor.stringFlavor) as? String ?: return false
                                    viewModel.pastedSongController.newSongFromText(text)
                                    return true
                                }
                                transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
                            } catch (exception: Exception) {
                                println("Could not read what was dropped: ${exception.message}")
                                return false
                            }
                            val paths = files.orEmpty().filterIsInstance<File>().map { it.absolutePath }
                            // Only the paths are taken here: this is the AWT event thread, and the files are read off it.
                            scope.launch { viewModel.importController.importFiles(withContext(Dispatchers.IO) { paths.readAsImportedFiles() }) }
                            return true
                        }
                    }
                },
            )
    ) {
        CampfireApp(
            viewModel = viewModel,
            urlOpener = { url -> if (!openUrl(url)) viewModel.exportController.onLinkNotOpened(url) },
            filesToImport = filesToImport,
            shareLinks = shareLinks,
            onBackgroundColorChanged = onBackgroundColorChanged,
        )
    }
}

/**
 * To be wired into the window's key event handler. Returns true if the event was consumed. It answers Escape alone,
 * which only the window turns into a back event; the shortcuts are the app's own (`appKeyboardShortcuts`), heard at the
 * root of the composition on every platform alike.
 *
 * @param onExit Closes the application, called when there is nothing left to navigate back from and the question that
 *   follows has been answered with Close, and only once a save that is still being written has finished, see
 *   `AppExitController.confirmExit` and [CampfireViewModel.requestExit].
 */
fun CampfireViewModel.handleKeyEvent(keyEvent: KeyEvent, onExit: () -> Unit): Boolean {
    // The shortcuts are the app's own root handler's (appKeyboardShortcuts), which every platform shares. Escape with Ctrl
    // or Cmd and without Alt neither goes back nor asks to close the app.
    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape &&
        !((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && !keyEvent.isAltPressed)
    ) {
        // Window key handlers run before Compose turns Escape into a back event, so consuming it here would pop the
        // back stack behind an open dialog, bottom sheet or overflow menu. Those register their own back handlers:
        // leaving the event unconsumed lets the top one dismiss itself (with its exit animation).
        if (dialogHost.visibleDialog.value != null || dialogHost.overlayState.isAnyMenuOpen) return false
        // The search is closed from here rather than by the back handler the field registers, since this handler sees
        // the key first and would consume it either way.
        val step = escapeStepOf(
            isReordering = setlistsController.isSetlistReordering,
            isSearchOpen = navigator.searches.isCurrentSearchOpen,
            isSelecting = songSelection.isSongSelecting,
            stackSize = navigator.backStack.size,
            isFilterShown = navigator.backStack.lastOrNull() == CampfireDestination.Songs &&
                songListState.isSongFilterActive.value,
        )
        when (step) {
            EscapeStep.CLOSE_SEARCH -> navigator.searches.closeCurrentSearch()
            EscapeStep.CLEAR_FILTERS -> songListState.clearSongFilter()
            EscapeStep.END_REORDERING, EscapeStep.END_SELECTION, EscapeStep.GO_BACK -> navigator.navigateBack()
            EscapeStep.EXIT -> appExitController.confirmExit(onExit)
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
 *
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
 * the first press after coming back would be taken for a repeat and swallowed.
 */
fun resetEscapeKey() {
    isEscapeHeld = false
}

/** One window, only ever touched on the AWT event thread, so a top-level flag is all the state [handlePreviewKeyEvent] needs. */
private var isEscapeHeld = false
