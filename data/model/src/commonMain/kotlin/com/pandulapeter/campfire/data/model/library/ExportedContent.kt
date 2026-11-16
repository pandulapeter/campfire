/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.library

/** What an [ExportedFile] holds: its bytes, or the temporary file it was written into, which is never held whole. */
sealed interface ExportedContent {

    val size: Long

    class Bytes(val bytes: ByteArray) : ExportedContent {

        override val size get() = bytes.size.toLong()

        override fun equals(other: Any?) = this === other || (other is Bytes && bytes.contentEquals(other.bytes))

        override fun hashCode() = bytes.contentHashCode()
    }

    /**
     * A file in the app's exports folder, at [path] as the platform names it (absolute, on the web from the root of the
     * origin private file system), and named as the [ExportedFile] is, so that a share sheet given it shows that name.
     * Whoever it is handed to (a platform's save or share) removes it, or moves it out of the way, once nothing reads it
     * any more; what is left of one is emptied with the folder on the next launch.
     */
    data class TemporaryFile(
        val path: String,
        override val size: Long,
    ) : ExportedContent
}
