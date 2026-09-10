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

import com.pandulapeter.campfire.data.model.domain.ImportedFile

/**
 * Zip handling, which has no state to cache: this exists so that the use cases can reach it without the domain layer
 * having to see the local sources.
 */
interface ArchiveRepository {

    /** See `ArchiveLocalSource.unpack`. */
    suspend fun unpack(archive: ByteArray): List<ImportedFile>

    /** See `ArchiveLocalSource.pack`. */
    suspend fun pack(files: Map<String, ByteArray>): ByteArray
}
