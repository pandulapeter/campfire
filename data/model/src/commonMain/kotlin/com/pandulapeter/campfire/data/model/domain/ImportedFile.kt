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
 * A file on its way into the library: what a file picker, a drop or an archive hands over, before anything has been
 * decided about it. [name] is a plain file name; entries coming out of an archive have their path stripped.
 *
 * A file that was not read is handed over all the same, with no bytes, so that it is reported rather than lost: one
 * the import would not look inside reads as skipped by its extension, one that could not be read holds nothing to
 * import, and one that was left unread for its size says so through [isTooLarge], see [ImportLimits].
 */
data class ImportedFile(
    val name: String,
    val bytes: ByteArray,
    val isTooLarge: Boolean = false,
) {

    override fun equals(other: Any?) =
        this === other || (other is ImportedFile && name == other.name && isTooLarge == other.isTooLarge && bytes.contentEquals(other.bytes))

    override fun hashCode() = 31 * (31 * name.hashCode() + isTooLarge.hashCode()) + bytes.contentHashCode()

    companion object {

        /** [name] as a file nobody read, see the class. */
        fun unread(name: String, isTooLarge: Boolean = false) = ImportedFile(name = name, bytes = ByteArray(0), isTooLarge = isTooLarge)
    }
}
