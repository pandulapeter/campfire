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

import androidx.compose.runtime.staticCompositionLocalOf
import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.data.model.importing.ImportedFile

/**
 * The platform's own file dialogs. Every shell provides one, because a picker is a piece of the host application (an
 * activity result launcher, an AWT dialog, a view controller, an `<input>` element) rather than of the shared UI.
 *
 * Both functions suspend until the user is done with the dialog, and are called from the view model's scope so that
 * the screen they were started from is free to go away in the meantime.
 */
interface FilePicker {

    /** Opens the system picker for one or more files. An empty list when the user picked nothing. */
    suspend fun pickFiles(): List<ImportedFile>

    /**
     * Offers the file to be saved: a "save as" dialog, a share sheet or a download. False when the user dismissed it
     * and nothing was saved, and when the platform could not show anything at all. A file that was meant to be saved
     * and could not be written is an exception, not a false: that is the one the caller tells the user about.
     */
    suspend fun saveFile(file: ExportedFile): Boolean

    /**
     * Hands the file to whatever the platform offers to send it with. Where there is no such thing, saving it is the
     * nearest equivalent and the default below does that, which is why [canShare] exists: a menu should not offer
     * "Share" next to "Export" when the two would do the same thing.
     */
    suspend fun shareFile(file: ExportedFile): Boolean = saveFile(file)

    /**
     * Hands [text], an address, to whatever the platform offers to send it with, [subject] being what a message it goes
     * out in is titled where the target takes one. Only asked where [canShare]; false where nothing was shown.
     */
    suspend fun shareText(text: String, subject: String): Boolean = false

    /** Whether there is a share sheet: [shareText] does something at all, and [shareFile] something other than [saveFile]. */
    val canShare: Boolean get() = false

    /**
     * Whether [shareFile] hands a file named [fileName], of [mimeType], to the share sheet rather than saving it. Asked per
     * kind of file because a browser shares only the kinds it trusts (Chrome takes a PDF, an image, or a `.txt`, never an
     * archive or a `.cho`), and a Share that would only download what Save downloads is not offered.
     */
    fun canShareFile(fileName: String, mimeType: String): Boolean = canShare

    /**
     * Opens a new mail to [to], titled [subject], with [file] attached, for the user to read and send. False where nothing
     * was shown, which the caller answers by sharing or saving the file instead: a device with no mail app or account,
     * and every platform where [canEmailFiles] is false, since a `mailto:` address carries no attachment.
     */
    suspend fun emailFile(file: ExportedFile, to: String, subject: String): Boolean = false

    /** Whether [emailFile] can do anything here, asked again each time, since a mail account can be set up meanwhile. */
    val canEmailFiles: Boolean get() = false
}

val LocalFilePicker = staticCompositionLocalOf<FilePicker> { error("No FilePicker has been provided.") }
