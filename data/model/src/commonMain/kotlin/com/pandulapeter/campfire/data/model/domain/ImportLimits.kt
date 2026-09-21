/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

/**
 * How much an import is willing to read. A song is a few kilobytes of text, and every way into the library reads a
 * file whole, so without a ceiling one wrong file in a selection is an allocation the process does not survive: an
 * Android app has a heap of about 192 MB to itself, and running out of it cannot be caught.
 *
 * The numbers are the same on every platform, so that an archive imports the same everywhere. At its worst an import
 * holds what was picked, what that unpacked to and the text of it, which is two bytes a character as soon as a song
 * steps outside Latin-1: four times [MAX_IMPORT_SIZE], which has to fit next to the app itself.
 */
object ImportLimits {

    /** One song, setlist or collection of songs, picked or inside an archive: a few thousand songs in one file. */
    const val MAX_TEXT_FILE_SIZE = 8L shl 20

    /**
     * One archive as it was picked, everything one selection reads, and everything one import unpacks to: more than
     * twice a library of three thousand songs.
     */
    const val MAX_IMPORT_SIZE = 24L shl 20

    /**
     * How many bytes of a file called [name] an import reads, which is none for a file it would not look inside.
     * Asked before the file is opened wherever the name is known first, which is everywhere.
     */
    fun maxSizeOf(name: String) = when {
        name.endsWith(LibraryFiles.ARCHIVE_EXTENSION, ignoreCase = true) -> MAX_IMPORT_SIZE
        LibraryFiles.IMPORTABLE_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) } -> MAX_TEXT_FILE_SIZE
        else -> 0L
    }
}

/**
 * What one selection - a pick, a drop, the files of an intent - may still read. Every platform reads its files
 * through one of these, so the rule is written once: nothing the import would not look inside is read, nothing over
 * its own limit is, and the selection as a whole stops at [ImportLimits.MAX_IMPORT_SIZE].
 */
class ImportBudget {

    @PublishedApi
    internal var remaining = ImportLimits.MAX_IMPORT_SIZE

    /**
     * @param size What the platform says the size of the file is, null where it cannot say.
     * @param readBytes Reads the file, and no more than one byte past `limit` where [size] was null - which is how a
     *   file of unknown size is found out. Null for a file that cannot be read, which is then left out.
     */
    inline fun read(name: String, size: Long?, readBytes: (limit: Long) -> ByteArray?): ImportedFile? {
        val ownLimit = ImportLimits.maxSizeOf(name)
        if (ownLimit == 0L) return ImportedFile.unread(name)
        val limit = minOf(ownLimit, remaining)
        if (size != null && size > limit) return ImportedFile.unread(name, isTooLarge = true)
        val bytes = readBytes(limit) ?: return null
        if (bytes.size > limit) return ImportedFile.unread(name, isTooLarge = true)
        remaining -= bytes.size
        return ImportedFile(name = name, bytes = bytes)
    }
}
