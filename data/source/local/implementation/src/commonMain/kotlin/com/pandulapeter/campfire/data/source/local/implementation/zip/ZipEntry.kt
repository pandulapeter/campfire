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

/**
 * One file inside a zip archive. [name] is the path as stored in the archive: forward slashes, possibly containing
 * sub-directories, which callers strip when they only want the file name.
 */
internal data class ZipEntry(
    val name: String,
    val bytes: ByteArray,
) {

    override fun equals(other: Any?) = this === other || (other is ZipEntry && name == other.name && bytes.contentEquals(other.bytes))

    override fun hashCode() = 31 * name.hashCode() + bytes.contentHashCode()
}
