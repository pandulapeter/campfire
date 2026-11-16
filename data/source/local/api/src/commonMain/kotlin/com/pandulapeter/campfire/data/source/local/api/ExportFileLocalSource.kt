/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.api

import com.pandulapeter.campfire.data.model.library.ExportFileWriter
import com.pandulapeter.campfire.data.model.library.ExportedFile

/**
 * The exports folder: the temporary files exports are written into before a platform saves or shares them, so that
 * none is ever held in memory whole. Nothing in it outlives the process that made it for long: [clear] empties it on
 * launch, and any first use waits for that.
 */
interface ExportFileLocalSource {

    /** Empties the folder of what an earlier process left (a crash, a share still being read). Called once on launch. */
    suspend fun clear()

    /** A new file called [name], replacing one of that name. Throws [LibraryStorageException] where it cannot be made. */
    suspend fun create(name: String): ExportFileWriter

    /**
     * [file]'s bytes, read whole: for a copy small enough to be sent in one request (a shared link's, which the import
     * limit bounds). Null where its temporary file is gone; throws [LibraryStorageException] where it cannot be read.
     */
    suspend fun read(file: ExportedFile): ByteArray?

    /** Removes the temporary file of [file], if it has one and it is still there. Never throws but cancellation. */
    suspend fun discard(file: ExportedFile)
}
