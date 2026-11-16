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

import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.library.LibraryFiles

/** Where a picture is picked from, asked where the platform has a photo library apart from its files ([FilePicker.canPickPhotos]). */
enum class PictureSource {
    PHOTOS,
    FILES,
}

/** Picks pictures from [source]: [FilePicker.pickPhotos] (one, unless [isMultiple]) or [FilePicker.pickImages]. */
suspend fun FilePicker.pickPictures(source: PictureSource, isMultiple: Boolean): List<ImportedFile> = when (source) {
    PictureSource.PHOTOS -> pickPhotos(isMultiple)
    PictureSource.FILES -> pickImages()
}

/**
 * [name] with the extension of the picture [mimeType] says it is, where it has none of a picture's: a gallery or a photo
 * library may hand a photo over under a bare number or a name of its own, and the import knows a picture alone by its
 * name. A name that already ends in a picture's extension is kept, even one that disagrees with [mimeType]: the import
 * goes by the bytes from there. Blank, it is called "photo".
 */
fun pictureFileName(name: String, mimeType: String?): String {
    val baseName = name.ifBlank { DEFAULT_PICTURE_NAME }
    val extension = PICTURE_EXTENSION_BY_MIME_TYPE[mimeType?.lowercase()] ?: return baseName
    return if (LibraryFiles.isImageFileName(baseName)) baseName else baseName + extension
}

private const val DEFAULT_PICTURE_NAME = "photo"

/** The pictures a song can show, and the ones the platforms with a photo library hand over to be converted. */
private val PICTURE_EXTENSION_BY_MIME_TYPE = mapOf(
    "image/png" to ".png",
    "image/jpeg" to ".jpg",
    "image/gif" to ".gif",
    "image/webp" to ".webp",
    "image/heic" to ".heic",
    "image/heif" to ".heif",
    "image/avif" to ".avif",
)
