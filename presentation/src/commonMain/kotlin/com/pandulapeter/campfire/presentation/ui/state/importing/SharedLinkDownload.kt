/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.data.model.importing.ImportDownload
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportFailure

/** What the download of a shared link comes to for the import: the zip to read, or why there is none. */
internal sealed interface SharedLinkDownload {

    class Zip(val file: ImportedFile) : SharedLinkDownload

    class Failed(val reason: ImportFailure) : SharedLinkDownload
}

/**
 * A shared link always serves a zip, so anything else that answers with a success is the host's page in its place (a
 * banned link, a login page, an empty file) and means the link no longer works; it is never read as a file of its own,
 * which would end in an unreadable row named after a random id. A refusal is the same: the download host answers a
 * stopped, revoked, or deleted link with a 4xx, and only no answer, a timeout, or a server error is worth trying again.
 */
internal fun ImportDownload.toSharedLinkDownload(): SharedLinkDownload = when (this) {
    is ImportDownload.File -> if (LibraryFiles.isZipArchive(bytes)) {
        SharedLinkDownload.Zip(ImportedFile(name = name, bytes = bytes))
    } else {
        SharedLinkDownload.Failed(ImportFailure.LINK_GONE)
    }

    ImportDownload.TooLarge -> SharedLinkDownload.Failed(ImportFailure.TOO_LARGE)
    ImportDownload.Missing -> SharedLinkDownload.Failed(ImportFailure.LINK_GONE)
    ImportDownload.Unreachable -> SharedLinkDownload.Failed(ImportFailure.LINK_UNREACHABLE)
}
