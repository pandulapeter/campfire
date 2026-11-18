/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.importing

import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.data.model.importing.ImportedFile

/**
 * The [ImportProgress.Phase.UNPACKING] progress of one batch of [files], told to [onProgress]: the files counted, and,
 * where the batch holds staged archives, the bytes their unpacks have read against the staged archives' sizes, each
 * nested archive's added as an unpack reaches it. A batch that is one large archive spends the phase in that archive,
 * so a file count would stand at 0 of 1 throughout. An archive is counted whole once it ends, read to its end or not
 * (a walk skips what it does not want, and one that fails or is too large is not read at all), so the bytes only grow.
 *
 * Told about one file at a time, in order.
 */
internal class UnpackingProgress(
    private val files: List<ImportedFile>,
    private val onProgress: (ImportProgress) -> Unit,
) {
    private val archiveBytes = files.sumOf { it.stagedArchiveSize }

    /** The bytes of the files before the current one, each archive's as its unpack last counted them. */
    private var finishedBytes = 0L

    /** The sizes of the nested archives the files before the current one staged. */
    private var nestedBytes = 0L

    private var index = 0
    private var readBytes = 0L
    private var currentBytes = 0L

    /** The file at [index] is about to be read. */
    fun start(index: Int) {
        this.index = index
        readBytes = 0
        currentBytes = files[index].stagedArchiveSize
        report()
    }

    /** The current file's unpack has read [readBytes] of its staged archives, [totalBytes] so far, nested ones included. */
    fun read(readBytes: Long, totalBytes: Long) {
        this.readBytes = readBytes
        currentBytes = totalBytes
        report()
    }

    /** The current file is done with, however it ended. */
    fun finish() {
        finishedBytes += currentBytes
        nestedBytes += currentBytes - files[index].stagedArchiveSize
        readBytes = 0
        currentBytes = 0
    }

    /** Every file is read. */
    fun end() = onProgress(
        ImportProgress(
            phase = ImportProgress.Phase.UNPACKING,
            completed = files.size,
            total = files.size,
            completedBytes = finishedBytes.takeIf { archiveBytes > 0 },
            totalBytes = finishedBytes.takeIf { archiveBytes > 0 },
        ),
    )

    private fun report() {
        val totalBytes = archiveBytes + nestedBytes + currentBytes - files[index].stagedArchiveSize
        onProgress(
            ImportProgress(
                phase = ImportProgress.Phase.UNPACKING,
                completed = index,
                total = files.size,
                fileName = files[index].name,
                completedBytes = (finishedBytes + readBytes).takeIf { archiveBytes > 0 },
                totalBytes = totalBytes.takeIf { archiveBytes > 0 },
            ),
        )
    }

    /** What the streaming unpack reads of the file: its staged copy, where it is an archive that has one. */
    private val ImportedFile.stagedArchiveSize get() = staged?.takeIf { isArchive }?.size ?: 0L
}
