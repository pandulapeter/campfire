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

import com.pandulapeter.campfire.data.model.domain.ExportedFile
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * `java.awt.FileDialog` rather than `JFileChooser`: it is the operating system's own dialog on macOS and Windows,
 * which is what a file picker is expected to look and behave like. It is given no parent window on purpose - the app
 * has exactly one, and a parentless dialog is still modal to the application.
 *
 * No file type filter: `.cho` has no registered type on any desktop platform, so filtering would hide the very files
 * the user came for. What is not a song is skipped by the import and reported afterwards.
 *
 * The dialog is shown on the AWT event thread, which is the only place a modal one blocks until it is dismissed:
 * from any other thread `isVisible = true` returns straight away and the picker would answer "nothing was picked"
 * while the dialog was still on screen. Reading and writing the files then goes back off it.
 */
internal object DesktopFilePicker : FilePicker {

    override suspend fun pickFiles(): List<ImportedFile> {
        val files = withContext(Dispatchers.Main) {
            FileDialog(null as Frame?, DIALOG_TITLE, FileDialog.LOAD).run {
                isMultipleMode = true
                isVisible = true
                this.files.orEmpty().toList()
            }
        }
        return withContext(Dispatchers.IO) {
            files.mapNotNull { file ->
                // One unreadable file must not lose the others that were picked with it.
                try {
                    ImportedFile(name = file.name, bytes = file.readBytes())
                } catch (exception: Exception) {
                    println("Could not read \"${file.name}\": ${exception.message}")
                    null
                }
            }
        }
    }

    override suspend fun saveFile(file: ExportedFile): Boolean {
        val target = withContext(Dispatchers.Main) {
            FileDialog(null as Frame?, DIALOG_TITLE, FileDialog.SAVE).run {
                this.file = file.name
                isVisible = true
                val directory = this.directory
                val name = this.file
                if (directory == null || name == null) null else File(directory, name)
            }
        } ?: return false
        return withContext(Dispatchers.IO) {
            target.writeBytes(file.bytes)
            true
        }
    }

    private const val DIALOG_TITLE = "Campfire"
}

/**
 * Reads whatever of the given paths can be read, which is how a file reaches the app without a dialog: as a command
 * line argument from an "open with", or as a drop onto the window. Anything unreadable is left out, and anything the
 * import does not recognise is reported by it as skipped.
 */
fun List<String>.readAsImportedFiles() = mapNotNull { path ->
    val file = File(path)
    try {
        if (file.isFile) ImportedFile(name = file.name, bytes = file.readBytes()) else null
    } catch (exception: Exception) {
        println("Could not read \"$path\": ${exception.message}")
        null
    }
}
