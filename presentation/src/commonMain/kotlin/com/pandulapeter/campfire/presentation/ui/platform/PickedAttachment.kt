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

import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.library.LibraryFiles

/**
 * Whether a file called [name] may be attached to a song: its extension, lowercased, is one of
 * [LibraryFiles.ATTACHMENT_EXTENSIONS]. A name with no extension never is.
 */
fun isAttachmentFileName(name: String) = name.contains('.') && ".${name.substringAfterLast('.').lowercase()}" in LibraryFiles.ATTACHMENT_EXTENSIONS

/**
 * The file a [FilePicker.pickAttachment] answers with for the one called [name] of [size] (null where the platform does
 * not say), read with [read] only where it may be attached: one of another type is handed back unread, and one larger
 * than [ImportLimits.MAX_PICKED_ATTACHMENT_SIZE] unread and [ImportedFile.isTooLarge], whether its size said so or the
 * read did. [read] is given the limit and reads at most a byte past it, which is how a file whose size was not known
 * says it is too large; a file it cannot read (null) is handed back empty.
 */
inline fun pickedAttachment(name: String, size: Long?, read: (limit: Long) -> ByteArray?): ImportedFile = when {
    !isAttachmentFileName(name) -> ImportedFile.unread(name)
    size != null && size > ImportLimits.MAX_PICKED_ATTACHMENT_SIZE -> ImportedFile.unread(name, isTooLarge = true)
    else -> read(ImportLimits.MAX_PICKED_ATTACHMENT_SIZE)?.let { bytes ->
        if (bytes.size > ImportLimits.MAX_PICKED_ATTACHMENT_SIZE) ImportedFile.unread(name, isTooLarge = true) else ImportedFile(name = name, bytes = bytes)
    } ?: ImportedFile.unread(name)
}
