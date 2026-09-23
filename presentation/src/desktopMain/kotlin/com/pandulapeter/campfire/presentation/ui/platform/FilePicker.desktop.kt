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
import com.pandulapeter.campfire.data.model.domain.ImportBudget
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
        return withContext(Dispatchers.IO) { files.map { it.absolutePath }.readAsImportedFiles() }
    }

    override suspend fun saveFile(file: ExportedFile): Boolean {
        val target = withContext(Dispatchers.Main) {
            FileDialog(null as Frame?, DIALOG_TITLE, FileDialog.SAVE).run {
                this.file = file.name
                isVisible = true
                val directory = this.directory
                val name = this.file
                if (directory == null || name == null) null else File(directory, name).withExtensionOf(file.name)
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
 * The file the user chose, with the extension of the one the app offered put back if they typed a name without it.
 * The dialog has no file type to add one by (see [DesktopFilePicker]), and a file named without its extension is one
 * the import skips, since that is what decides what a file is. Not where a file already has that name: the dialog
 * asked about replacing the name that was typed, and nothing it did not ask about is written over, so the name is
 * then left exactly as typed.
 */
private fun File.withExtensionOf(offeredName: String): File {
    val extension = offeredName.substringAfterLast('.', missingDelimiterValue = "")
    if (extension.isEmpty() || name.endsWith(".$extension", ignoreCase = true)) return this
    val extended = File(parentFile, "$name.$extension")
    return if (extended.exists()) this else extended
}

/**
 * Reads whatever of the given paths can be read, within one [ImportBudget], which is also how a file reaches the app
 * without a dialog: as a command line argument from an "open with", or as a drop onto the window. A folder stands for
 * the files directly inside it - not for the folders in there, and not for the hidden files nobody chose. Anything
 * unreadable is left out, so that one bad file does not lose the ones next to it, and anything the import does not
 * recognise is reported by it as skipped.
 */
fun List<String>.readAsImportedFiles(): List<ImportedFile> {
    val budget = ImportBudget()
    return flatMap { path ->
        val file = File(path)
        if (file.isDirectory) {
            file.listFiles { child -> child.isFile && !child.name.startsWith(".") }.orEmpty().sortedBy { it.name }
        } else {
            listOf(file)
        }
    }.mapNotNull { file ->
        try {
            if (file.isFile) budget.read(name = file.name, size = file.length()) { file.readBytes() } else null
        } catch (exception: Exception) {
            println("Could not read \"${file.path}\": ${exception.message}")
            null
        }
    }
}
