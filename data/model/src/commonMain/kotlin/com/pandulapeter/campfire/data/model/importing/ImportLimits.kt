/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.importing

import com.pandulapeter.campfire.data.model.library.LibraryFiles

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

    /**
     * One song, setlist or collection of songs, picked or inside an archive: a few thousand songs in one file. A text
     * pasted as a new song is refused over it with a message that names it as 8 MB in both languages, so the strings
     * change with it.
     */
    const val MAX_TEXT_FILE_SIZE = 8L shl 20

    /** Documents may carry embedded fonts and images much larger than their extracted text. */
    const val MAX_DOCUMENT_FILE_SIZE = 16L shl 20

    /**
     * One archive as it was picked, everything one selection reads, and everything one import unpacks to: more than
     * twice a library of three thousand songs of 4 KB. A library exports to an archive of its own size plus about 180
     * bytes a file, so one of about six thousand such songs exports to an archive this refuses, and the export says so.
     * That message names the limit as 24 MB in both languages, so the strings change with it.
     */
    const val MAX_IMPORT_SIZE = 24L shl 20

    /**
     * How many levels of folders a dropped folder is opened to, its own being the first: a library exported as folders is
     * two deep, and a home directory dropped by accident is not read to the bottom.
     */
    const val MAX_FOLDER_DEPTH = 8

    /** How many entries, files and folders alike, are looked at below one dropped folder before the rest is left unread. */
    const val MAX_FOLDER_ENTRIES = 10_000

    /**
     * How many bytes of a file under a name the import does not know are looked at to decide whether to read the rest:
     * inside an archive and inside a dropped folder, where nobody chose the file itself and reading every photo next to
     * the songs whole would cost the whole selection's budget, one file at a time.
     */
    const val SNIFF_LENGTH = 512

    /**
     * How many bytes of a file called [name] an import reads, which is none for a file it would not look inside.
     * Asked before the file is opened wherever the name is known first, which is everywhere. A name it does not know at
     * all is a question for [ImportBudget], which reads such a file to see whether it is an archive under another name,
     * and so is a text name over its limit (see [isReadAsText]).
     */
    fun maxSizeOf(name: String) = when {
        LibraryFiles.isArchiveFileName(name) -> MAX_IMPORT_SIZE
        LibraryFiles.isDocumentFileName(name) -> MAX_DOCUMENT_FILE_SIZE
        LibraryFiles.IMPORTABLE_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) } -> MAX_TEXT_FILE_SIZE
        else -> 0L
    }

    /**
     * Whether a file called [name] is read as a song, setlist or collection of them, with [MAX_TEXT_FILE_SIZE] as its
     * limit. One over that limit is still kept where its bytes are a zip archive: a backup renamed `.txt` or `.json` on
     * its way over is often larger than any text.
     */
    fun isReadAsText(name: String) = maxSizeOf(name) == MAX_TEXT_FILE_SIZE
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
        if (!LibraryFiles.isImportableFileName(name)) return readUnknown(name, size, readBytes)
        val ownLimit = ImportLimits.maxSizeOf(name)
        // A text name is read as far as the selection allows rather than to its own limit, since one over that limit may
        // be a backup renamed on its way over (see ImportLimits.isReadAsText). A text that is not one is then handed back
        // too large, without having cost the files after it anything.
        val limit = if (ImportLimits.isReadAsText(name)) remaining else minOf(ownLimit, remaining)
        if (size != null && size > limit) return ImportedFile.unread(name, isTooLarge = true)
        val bytes = readBytes(limit) ?: return null
        if (bytes.size > limit || (bytes.size > ownLimit && !LibraryFiles.isZipArchive(bytes))) {
            return ImportedFile.unread(name, isTooLarge = true)
        }
        remaining -= bytes.size
        return ImportedFile(name = name, bytes = bytes)
    }

    /**
     * A file whose name the import does not know is read as an archive would be, since another app's library backup is
     * often a zip archive under a name of its own, and kept where its bytes say it is one. One whose bytes read as text
     * within [ImportLimits.MAX_TEXT_FILE_SIZE] is kept too, flagged [ImportedFile.isPlainTextByContent], since a chord
     * sheet saved by another app is as often named for that app; whether it is one is the import's question. Anything
     * else - the photo or the recording picked with the songs - is handed back unread and gives back what it took, so
     * that it neither crowds the files after it out of the selection nor is reported as too large rather than as
     * unsupported: nothing says it was ever meant to be imported.
     */
    @PublishedApi
    internal inline fun readUnknown(name: String, size: Long?, readBytes: (limit: Long) -> ByteArray?): ImportedFile? {
        val limit = remaining
        if (size != null && size > limit) return ImportedFile.unread(name)
        val bytes = readBytes(limit) ?: return null
        if (bytes.size > limit) return ImportedFile.unread(name)
        val isArchive = LibraryFiles.isZipArchive(bytes)
        val isText = !isArchive && isTextOfUnknownName(name, bytes)
        if (!isArchive && !isText) return ImportedFile.unread(name)
        remaining -= bytes.size
        return ImportedFile(name = name, bytes = bytes, isPlainTextByContent = isText)
    }

    /** Whether [bytes] of a file called [name], a name the import does not know, are kept as a text to look at. */
    @PublishedApi
    internal fun isTextOfUnknownName(name: String, bytes: ByteArray) = bytes.size <= ImportLimits.MAX_TEXT_FILE_SIZE &&
        !LibraryFiles.isBinaryFileName(name) && LibraryFiles.isTextLike(bytes.copyOf(minOf(bytes.size, ImportLimits.SNIFF_LENGTH)))

    companion object {

        /**
         * Whether a file under a name the import does not know is worth reading whole, judged by its first
         * [ImportLimits.SNIFF_LENGTH] bytes ([head]): an archive, or a text that may be a chord sheet. Asked by the
         * folder walks, which read nothing of a file that is not, so that a dropped folder of photos costs a peek at
         * each rather than a read of each; a file of one of the [LibraryFiles.BINARY_EXTENSIONS] is not even peeked at.
         */
        fun isWorthReadingUnknown(head: ByteArray) = LibraryFiles.isZipArchive(head) || LibraryFiles.isTextLike(head)
    }
}
