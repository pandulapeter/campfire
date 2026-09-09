package com.pandulapeter.campfire.presentation.ui

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
import com.pandulapeter.campfire.presentation.ui.platform.DesktopFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.readAsImportedFiles
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.compose.viewmodel.koinViewModel
import java.awt.Desktop
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.net.URI

/**
 * Desktop shell of the shared UI. Desktop has no back gesture, so the Escape key (see [handleKeyEvent]) dismisses the
 * visible modal, pops the back stack when there is none, clears the Songs search query on the root screen if it's
 * not already empty, and closes the application otherwise.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CampfireDesktopApp(
    viewModel: CampfireViewModel = koinViewModel(),
    filesToImport: Flow<List<ImportedFile>> = emptyFlow()
) = CompositionLocalProvider(
    LocalFilePicker provides DesktopFilePicker
) {
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
                            viewModel.importFiles(paths.readAsImportedFiles())
                            return true
                        }
                    }
                }
            )
    ) {
        CampfireApp(
            viewModel = viewModel,
            urlOpener = ::openUrl,
            filesToImport = filesToImport
        )
    }
}


/**
 * To be wired into the window's key event handler. Returns true if the event was consumed.
 *
 * @param onExit Closes the application, called when there is nothing left to navigate back from.
 */
fun CampfireViewModel.handleKeyEvent(keyEvent: KeyEvent, onExit: () -> Unit): Boolean {
    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
        // Window key handlers run before Compose turns Escape into a back event, so consuming it here would pop the
        // back stack behind an open dialog or bottom sheet. Those register their own back handlers: leaving the event
        // unconsumed lets the top one dismiss itself (with its exit animation).
        if (visibleDialog.value != null) return false
        when {
            backStack.size > 1 -> navigateBack()
            query.value.isNotEmpty() -> onQueryChanged("")
            else -> onExit()
        }
        return true
    }
    return false
}

private fun openUrl(url: String) {
    try {
        val desktop = Desktop.getDesktop()
        val osName by lazy(LazyThreadSafetyMode.NONE) { System.getProperty("os.name").lowercase() }
        when {
            Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.BROWSE) -> desktop.browse(URI(url))
            "mac" in osName -> Runtime.getRuntime().exec(arrayOf("open", url))
            "nix" in osName || "nux" in osName -> Runtime.getRuntime().exec(arrayOf("xdg-open", url))
            else -> println("Cannot open url: $url")
        }
    } catch (_: NoClassDefFoundError) {
        println("Cannot open url: $url")
    }
}
