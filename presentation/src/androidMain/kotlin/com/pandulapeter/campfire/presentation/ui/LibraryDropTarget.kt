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

import android.content.ClipDescription
import android.view.DragEvent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import com.pandulapeter.campfire.presentation.ui.platform.toImportedFiles
import com.pandulapeter.campfire.presentation.ui.state.importing.isDropAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Files dragged onto the app from another one - side by side in split screen, in a desktop window, or from the taskbar -
 * are imported, as the desktop's dropped files are, and a dragged text is a new song where Paste from clipboard would
 * make one. A drag that carries both is its files. Read only mode takes neither.
 */
@Composable
internal fun Modifier.libraryDropTarget(viewModel: CampfireViewModel): Modifier {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val target = remember(viewModel, activity, scope) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                // Asked again rather than trusted from the start of the drag, during which a dialog, the editor or
                // read only mode can have come.
                if (!viewModel.isDropAvailable) return false
                val dragEvent = event.toAndroidDragEvent()
                val clipData = dragEvent.clipData ?: return false
                val items = (0 until clipData.itemCount).map(clipData::getItemAt)
                val uris = items.mapNotNull { it.uri }
                if (uris.isEmpty()) {
                    if (!viewModel.pastedSongController.isNewSongPasteAvailable) return false
                    val text = items.mapNotNull { it.text?.toString() }.joinToString("\n").ifEmpty { return false }
                    viewModel.pastedSongController.newSongFromText(text)
                    return true
                }
                // Another app's content URIs can be read only under the permissions this grants, which must be asked
                // for while the drop is being answered and last until released or until the activity is gone.
                val permissions = try {
                    activity?.requestDragAndDropPermissions(dragEvent)
                } catch (exception: SecurityException) {
                    println("Could not read what was dropped: ${exception.message}")
                    return false
                }
                val context = activity?.applicationContext ?: return false
                scope.launch {
                    val files = try {
                        withContext(Dispatchers.IO) { uris.toImportedFiles(context) }
                    } finally {
                        permissions?.release()
                    }
                    viewModel.importController.importFiles(files)
                }
                return true
            }
        }
    }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { event -> viewModel.isDropAvailable && event.toAndroidDragEvent().isAcceptable(viewModel) },
        target = target,
    )
}

/**
 * A text is only offered where it makes a song, while a file is taken anywhere, the import waiting for whatever is open.
 * The description is all a drag shows before its drop, and a text file carries the same type as a text, so a plain
 * text file dragged elsewhere than the song list is refused with the texts.
 */
private fun DragEvent.isAcceptable(viewModel: CampfireViewModel): Boolean {
    val description = clipDescription ?: return false
    val isTextOnly = (0 until description.mimeTypeCount).all { index ->
        description.getMimeType(index).let { it == ClipDescription.MIMETYPE_TEXT_PLAIN || it == ClipDescription.MIMETYPE_TEXT_HTML }
    }
    return !isTextOnly || viewModel.pastedSongController.isNewSongPasteAvailable
}
