/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.data.model.importing.ImportIntake
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.domain.api.importing.ReceiveImportArchiveUseCase

/**
 * Whether this is an archive the preparation would unpack from memory, which [ImportWork] stages first while archives are
 * staged ([ImportIntake.stagesArchives]): the intake stages every archive it is handed, but a file it read whole may
 * still turn out to be one by its bytes (a picture's name over a zip, a head that could not be peeked at), and so may a
 * download's body that is no zip under an archive's name. One past [ImportLimits.MAX_ARCHIVE_SIZE] or with nothing read
 * is left as it is, for the preparation to report without unpacking it.
 */
internal val ImportedFile.isArchiveHeldInMemory
    get() = ImportIntake.stagesArchives && staged == null && receiving == null && !isTooLarge && bytes.isNotEmpty() &&
        bytes.size <= ImportLimits.MAX_ARCHIVE_SIZE && isArchive

/**
 * [bytes] written into the staging folder as a received archive of a request of its own, a slice at a time, so that a
 * Cancel is noticed between slices. Whatever ends it but its finish deletes what was written.
 */
internal suspend fun ReceiveImportArchiveUseCase.stage(bytes: ByteArray): StagedFile {
    val archive = this(bytes.size.toLong())
    try {
        var offset = 0
        while (offset < bytes.size) {
            val length = minOf(STAGED_SLICE_SIZE, bytes.size - offset)
            archive.write(bytes, offset, length)
            offset += length
        }
        return archive.finish()
    } catch (throwable: Throwable) {
        archive.abandon()
        throw throwable
    }
}

private const val STAGED_SLICE_SIZE = 256 * 1024
