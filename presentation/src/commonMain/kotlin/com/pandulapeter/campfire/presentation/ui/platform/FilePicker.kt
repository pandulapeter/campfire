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
import com.pandulapeter.campfire.data.model.domain.ExportedFile
import com.pandulapeter.campfire.data.model.domain.ImportedFile

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

    /** Offers the file to be saved: a "save as" dialog, a share sheet or a download. False when nothing was saved. */
    suspend fun saveFile(file: ExportedFile): Boolean

    /**
     * Hands the file to whatever the platform offers to send it with. Where there is no such thing, saving it is the
     * nearest equivalent and the default below does that, which is why [canShare] exists: a menu should not offer
     * "Share" next to "Export" when the two would do the same thing.
     */
    suspend fun shareFile(file: ExportedFile): Boolean = saveFile(file)

    /** Whether [shareFile] does something other than [saveFile]. */
    val canShare: Boolean get() = false
}

val LocalFilePicker = staticCompositionLocalOf<FilePicker> { error("No FilePicker has been provided.") }
