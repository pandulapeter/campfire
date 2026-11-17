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
 * Where the content of a picture or attachment an import brings is: in memory ([Bytes]: a picture link's download, a
 * picture picked on its own, an archive read whole), or in the staging folder ([Staged]: unpacked from an archive read
 * from there), which is never read whole.
 */
sealed interface IncomingContent {

    /** How many bytes the content holds. */
    val size: Long

    class Bytes(val bytes: ByteArray) : IncomingContent {

        override val size get() = bytes.size.toLong()

        override fun equals(other: Any?) = this === other || (other is Bytes && bytes.contentEquals(other.bytes))

        override fun hashCode() = bytes.contentHashCode()

        override fun toString() = "Bytes(size=${bytes.size})"
    }

    data class Staged(val file: StagedFile) : IncomingContent {

        override val size get() = file.size
    }
}
