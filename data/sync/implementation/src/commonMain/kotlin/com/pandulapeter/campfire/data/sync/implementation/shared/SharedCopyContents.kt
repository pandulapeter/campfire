/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.shared

import com.pandulapeter.campfire.data.model.share.SharedLinkKind

/**
 * The few looks [SharedCopySync] takes inside library files, which sync otherwise treats as bytes: the songs a setlist
 * names, the title an item goes by, and the zip a copy is packed into. Apart from the step so that its tests need no
 * setlist or ChordPro format.
 */
internal interface SharedCopyContents {

    /** The song file names the setlist document [bytes] names, in its order, or null where it is no setlist. */
    suspend fun setlistSongFileNames(bytes: ByteArray): List<String>?

    /** The title the library file [fileName], holding [bytes], goes by in the app, or null where it cannot be read. */
    suspend fun titleOf(kind: SharedLinkKind, fileName: String, bytes: ByteArray): String?

    /** [files] packed undated, so that the same files make the same bytes on every device and at any time. */
    suspend fun pack(files: Map<String, ByteArray>): ByteArray
}
