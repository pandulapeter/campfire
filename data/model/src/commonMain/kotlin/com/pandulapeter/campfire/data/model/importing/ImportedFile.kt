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

/**
 * A file on its way into the library: what a file picker, a drop or an archive hands over, before anything has been
 * decided about it. [name] is a plain file name; entries coming out of an archive have their path stripped.
 *
 * A file that was not read is handed over all the same, with no bytes, so that it is reported rather than lost: one
 * the import would not look inside reads as skipped by its extension, one that could not be read holds nothing to
 * import, and one that was left unread for its size says so through [isTooLarge], see [ImportLimits].
 *
 * [isPlainTextByContent] marks a file kept for its bytes alone, under a name the import does not know: read as plain
 * text, and only where it has the structure of a chord sheet. The flag is carried rather than the name changed, since
 * the import reports a file under the name it came under.
 */
data class ImportedFile(
    val name: String,
    val bytes: ByteArray,
    val isTooLarge: Boolean = false,
    val isPlainTextByContent: Boolean = false,
) {

    /**
     * [name] without its extension, which titles a song that names none itself. A file kept for its text loses only an
     * extension with no space in it: `riverlight.onsong` is "riverlight", but `Mr. Brightside` is a title whose dot
     * ends an abbreviation.
     */
    val baseName: String
        get() {
            val extension = name.substringAfterLast('.', "")
            val hasExtension = !isPlainTextByContent || (extension.isNotEmpty() && extension.none(Char::isWhitespace))
            return if (hasExtension) name.substringBeforeLast('.') else name
        }

    override fun equals(other: Any?) = this === other || (
        other is ImportedFile && name == other.name && isTooLarge == other.isTooLarge &&
            isPlainTextByContent == other.isPlainTextByContent && bytes.contentEquals(other.bytes)
        )

    override fun hashCode() =
        31 * (31 * (31 * name.hashCode() + isTooLarge.hashCode()) + isPlainTextByContent.hashCode()) + bytes.contentHashCode()

    companion object {

        /** [name] as a file nobody read, see the class. */
        fun unread(name: String, isTooLarge: Boolean = false) = ImportedFile(name = name, bytes = ByteArray(0), isTooLarge = isTooLarge)
    }
}
