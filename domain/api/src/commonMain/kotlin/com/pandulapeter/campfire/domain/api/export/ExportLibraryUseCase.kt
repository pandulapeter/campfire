/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.export

import com.pandulapeter.campfire.data.model.library.ExportedFile

interface ExportLibraryUseCase {

    /**
     * A zip of every song and setlist, and the pictures the songs show under `images/`. Null when the library is empty **or could not be read**: an archive missing a
     * library it was supposed to contain is worse than no archive, since the user files it away as a backup.
     *
     * Throws [TooLargeException], before anything is written, for a library whose export would not fit [MEMORY_BUDGET].
     */
    suspend operator fun invoke(): Result?

    /**
     * [file] plus the names of the files the export could not read, which are left out of it: a song the library scan
     * skipped, one whose text could not be read, a setlist whose document could not be. On the web an export is the
     * only copy of the library there is, so a name missing from the archive is told to the user rather than logged.
     */
    data class Result(
        val file: ExportedFile,
        val skippedFileNames: List<String>,
    )

    /** A library of [fileCount] files, the largest of [largestFileSize] bytes, too large to export within [MEMORY_BUDGET]. */
    class TooLargeException(val fileCount: Int, val largestFileSize: Long) :
        Exception("A library of $fileCount files, the largest of $largestFileSize bytes, does not export within $MEMORY_BUDGET bytes.")

    companion object {

        /**
         * The memory a library export may need, so that it never runs out of a 128 MiB heap, Android's smallest. The
         * archive is written as it is read, but the writer keeps every entry's name, offset, CRC, and sizes for the central
         * directory, and the export its listing of the library: [BYTES_PER_FILE] a file in all. And it reads each entry
         * whole, deflating one that compresses into a buffer of its own size, so the largest file takes twice its size at
         * its turn. Measured in a JVM capped at 128 MiB: 17,997 files ending in a 50 MiB attachment that compresses
         * exported (about 125 MB by this sum) and 18,987 ran out, so the budget leaves some 10 MB for the rest of the app.
         * That still exports about 100,000 files without a large attachment, or about 14,000 beside a 50 MiB one.
         */
        const val MEMORY_BUDGET = 115L * 1024 * 1024

        /** What one file of a library export keeps in memory until the archive ends; see [MEMORY_BUDGET]. */
        const val BYTES_PER_FILE = 1_100L

        /** Whether a library of [fileCount] files, the largest of [largestFileSize] bytes, exports within [budget]. */
        fun fits(fileCount: Int, largestFileSize: Long, budget: Long = MEMORY_BUDGET) =
            fileCount * BYTES_PER_FILE + 2 * largestFileSize <= budget
    }
}
