/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.formats.image.ImageFormats
import com.pandulapeter.campfire.data.formats.image.ImageInfo
import com.pandulapeter.campfire.data.model.image.ImageRefusal
import com.pandulapeter.campfire.data.model.image.ImageSize
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.hashing.Sha256

/** The rules a picture or an attachment is taken into the library by, the same for every way it arrives. */
internal object LibraryImages {

    /** What [examine] found: why the picture is refused, else the extension it is named with and its size. */
    class Examined(val refusal: ImageRefusal?, val extension: String, val size: ImageSize?)

    /** Judged by the bytes alone, never by the name a picture arrived with. */
    fun examine(bytes: ByteArray): Examined {
        if (bytes.size > ImportLimits.MAX_IMAGE_FILE_SIZE) return Examined(ImageRefusal.TOO_LARGE, "", null)
        val info = ImageInfo.of(bytes) ?: return Examined(
            refusal = when {
                ImageInfo.isOverPixelLimit(bytes) -> ImageRefusal.TOO_MANY_PIXELS
                ImageFormats.isRefusedImage(bytes) -> ImageRefusal.UNSUPPORTED_FORMAT
                else -> ImageRefusal.NOT_AN_IMAGE
            },
            extension = "",
            size = null,
        )
        return Examined(refusal = null, extension = info.format.extension, size = ImageSize(width = info.width, height = info.height))
    }

    /**
     * Judged by its size and the [extension] it arrived with alone, lowercased (with or without its dot): an attachment
     * may be anything of [LibraryFiles.ATTACHMENT_EXTENSIONS], so its bytes say nothing a header could check.
     */
    fun examineAttachment(bytes: ByteArray, extension: String): Examined {
        val named = extension.lowercase().let { if (it.startsWith('.')) it else ".$it" }
        return when {
            bytes.size > ImportLimits.MAX_ATTACHMENT_FILE_SIZE -> Examined(ImageRefusal.TOO_LARGE, "", null)
            named !in LibraryFiles.ATTACHMENT_EXTENSIONS -> Examined(ImageRefusal.UNSUPPORTED_FORMAT, "", null)
            else -> Examined(refusal = null, extension = named, size = null)
        }
    }

    /**
     * The names [bytes] may take, shortest first: its SHA-256's first [LibraryFiles.MIN_IMAGE_NAME_DIGITS] hex digits,
     * then one more at a time, for the rare different picture that took the shorter one. Every one passes
     * [LibraryFiles.isLibraryFileNamedFor], which is how sync tells a picture from a damaged one. Hashing an attachment
     * takes seconds, so the caller runs this on a computation dispatcher.
     */
    suspend fun contentNames(bytes: ByteArray, extension: String): Sequence<String> {
        val hex = Sha256.hashToHexYielding(bytes)
        return (LibraryFiles.MIN_IMAGE_NAME_DIGITS..LibraryFiles.MAX_IMAGE_NAME_DIGITS).asSequence().map { hex.take(it) + extension }
    }
}
