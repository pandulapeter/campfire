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
import com.pandulapeter.campfire.data.source.remote.api.hashing.Sha256

/** The rules a picture is taken into the library by, the same for every way it arrives. */
internal object LibraryImages {

    /** What [examine] found: why the picture is refused, else the extension it is named with and its size. */
    class Examined(val refusal: ImageRefusal?, val extension: String, val size: ImageSize?)

    /** Judged by the bytes alone, never by the name a picture arrived with. */
    fun examine(bytes: ByteArray): Examined {
        if (bytes.size > ImportLimits.MAX_IMAGE_FILE_SIZE) return Examined(ImageRefusal.TOO_LARGE, "", null)
        val info = ImageInfo.of(bytes) ?: return Examined(
            refusal = if (ImageFormats.isRefusedImage(bytes)) ImageRefusal.UNSUPPORTED_FORMAT else ImageRefusal.NOT_AN_IMAGE,
            extension = "",
            size = null,
        )
        return Examined(refusal = null, extension = info.format.extension, size = ImageSize(width = info.width, height = info.height))
    }

    /**
     * The names [bytes] may take, shortest first: its SHA-256's first [LibraryFiles.MIN_IMAGE_NAME_DIGITS] hex digits,
     * then one more at a time, for the rare different picture that took the shorter one. Every one passes
     * [LibraryFiles.isImageNamedFor], which is how sync tells a picture from a damaged one.
     */
    fun contentNames(bytes: ByteArray, extension: String): Sequence<String> {
        val hex = Sha256.hashToHex(bytes)
        return (LibraryFiles.MIN_IMAGE_NAME_DIGITS..LibraryFiles.MAX_IMAGE_NAME_DIGITS).asSequence().map { hex.take(it) + extension }
    }
}
