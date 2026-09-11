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
 */
data class ImportedFile(
    val name: String,
    val bytes: ByteArray,
) {

    override fun equals(other: Any?) = this === other || (other is ImportedFile && name == other.name && bytes.contentEquals(other.bytes))

    override fun hashCode() = 31 * name.hashCode() + bytes.contentHashCode()
}
