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

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.data.model.library.ExportedFile

/**
 * Hands a file to the app the operating system picks for its type, which is how a song's attachment is opened: Android's
 * `ACTION_VIEW`, iOS's Quick Look (whose share button offers every other app), the desktop's default application, and on
 * the web a new tab where the browser shows the type, else a download.
 */
fun interface FileOpener {

    /**
     * Opens [file], a copy in the exports folder named as it is to be shown, which is the opener's from then on as an
     * export handed to `FilePicker` is. False where nothing on the device takes its type, which the caller says; an
     * exception where the platform failed for another reason.
     */
    suspend fun open(file: ExportedFile): Boolean
}

/** The platform's [FileOpener], tied to the window it presents from where it presents anything (iOS). */
@Composable
internal expect fun rememberFileOpener(): FileOpener
