/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.library

import com.pandulapeter.campfire.data.model.importing.ImportLimits

/**
 * A file on its way out of the library. The name and the type are decided where the content is (a lone song leaves
 * as the `.cho` file it already is, everything else as a zip), so that the platform's save dialog only has to hand
 * the [content] over: bytes for the small cases, the temporary file an archive or a PDF was written into for the rest.
 */
data class ExportedFile(
    val name: String,
    val mimeType: String,
    val content: ExportedContent,
    /** What an import unpacks the file to: an archive's entries added up, which its compressed [content] is smaller than. */
    val unpackedSize: Long = content.size,
    /** The part of [unpackedSize] that is pictures (an archive's `images/`), which an import reads within a budget of its own. */
    val unpackedImageSize: Long = 0,
) {

    constructor(name: String, mimeType: String, bytes: ByteArray) : this(name = name, mimeType = mimeType, content = ExportedContent.Bytes(bytes))

    /**
     * Whether an import would not take the archive whole: one larger than it reads at all, unpacking to more songs than
     * it holds, or to more pictures than it reads, the rest of which it leaves out with their songs still imported.
     */
    val isTooLargeToImport
        get() = mimeType == ZIP_MIME_TYPE && (
            content.size > ImportLimits.MAX_ARCHIVE_SIZE ||
                unpackedSize - unpackedImageSize > ImportLimits.MAX_IMPORT_SIZE ||
                unpackedImageSize > ImportLimits.MAX_IMPORT_IMAGES_SIZE
            )

    /** Whether the archive is larger, packed or unpacked, than a shared link's copy may be ([ImportLimits.MAX_SHARED_COPY_SIZE]). */
    val isTooLargeToShare get() = mimeType == ZIP_MIME_TYPE && maxOf(content.size, unpackedSize) > ImportLimits.MAX_SHARED_COPY_SIZE

    companion object {
        const val TEXT_MIME_TYPE = "text/plain"
        const val ZIP_MIME_TYPE = "application/zip"
        const val PNG_MIME_TYPE = "image/png"
        const val PDF_MIME_TYPE = "application/pdf"
    }
}
