/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.zip

/** What [ZipReader.read] made of an archive: the entries it read, and the names of the ones it did not, with why. */
internal class ZipContent(
    val entries: List<ZipEntry>,
    val unread: List<UnreadZipEntry>,
    /** What the archive's reads cost the budget of [ZipReader.read], damaged entries included. */
    val chargedSize: Long,
)

internal data class UnreadZipEntry(
    val name: String,
    val reason: Reason,
) {

    enum class Reason {
        /** The caller had no use for it, so nothing but its name was looked at. */
        NOT_WANTED,

        /** Larger than the caller allows a file of that name to be, or than what is left of the archive's limit. */
        TOO_LARGE,

        /** Encrypted, ZIP64, compressed with a method other than DEFLATE, or damaged. */
        UNREADABLE,
    }
}
