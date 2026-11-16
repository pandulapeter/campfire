/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.library.ExportFileWriter
import com.pandulapeter.campfire.data.model.library.ExportedFile

/** The temporary files exports are written into; see `ExportFileLocalSource`, which this only passes through. */
interface ExportFileRepository {

    suspend fun clear()

    suspend fun create(name: String): ExportFileWriter

    suspend fun read(file: ExportedFile): ByteArray?

    suspend fun discard(file: ExportedFile)
}
