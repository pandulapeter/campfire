/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.export

import com.pandulapeter.campfire.data.model.library.ArchiveEntry
import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.ExportFileRepository

/**
 * [entries] as a zip archive called [name], written into a temporary file one entry at a time, knowing what an import
 * would unpack it to, since its entries are compressed. Null, the file removed, where not one entry could be read.
 * Whatever ends the packing early (a failure, a cancellation) removes the file before it is passed on.
 */
internal suspend fun exportedArchive(
    archiveRepository: ArchiveRepository,
    exportFileRepository: ExportFileRepository,
    name: String,
    entries: List<suspend () -> ArchiveEntry?>,
    isDated: Boolean,
): ExportedFile? {
    val writer = exportFileRepository.create(name)
    val packed = try {
        archiveRepository.pack(entries = entries, isDated = isDated, sink = writer)
    } catch (throwable: Throwable) {
        writer.abandon()
        throw throwable
    }
    if (packed.entryCount == 0) {
        writer.abandon()
        return null
    }
    val content = try {
        writer.finish()
    } catch (throwable: Throwable) {
        writer.abandon()
        throw throwable
    }
    return ExportedFile(name = name, mimeType = ExportedFile.ZIP_MIME_TYPE, content = content, unpackedSize = packed.unpackedSize)
}
