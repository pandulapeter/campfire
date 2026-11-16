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

/** Where an export is written as it is made, front to back: an archive entry by entry, a PDF page by page. */
interface ExportSink {

    /** How many bytes have been written so far. */
    val position: Long

    /** May throw `LibraryStorageException` where the file cannot be written, which ends the export. */
    suspend fun write(bytes: ByteArray, offset: Int, length: Int)
}
