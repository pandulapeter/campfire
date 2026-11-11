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

import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.data.repository.api.ArchiveRepository

/** [files] as a zip archive called [name], knowing what an import would unpack it to, since its entries are compressed. */
internal suspend fun ArchiveRepository.exportedArchive(name: String, files: Map<String, ByteArray>, isDated: Boolean) = ExportedFile(
    name = name,
    mimeType = ExportedFile.ZIP_MIME_TYPE,
    bytes = pack(files = files, isDated = isDated),
    unpackedSize = files.values.sumOf { it.size.toLong() },
)
