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
     * Throws [TooLargeException], before anything is written, for a library of more than [MAX_FILE_COUNT] files.
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

    /** A library of [fileCount] files, more than [MAX_FILE_COUNT], which is not exported at all. */
    class TooLargeException(val fileCount: Int) : Exception("A library of $fileCount files is more than one archive exports ($MAX_FILE_COUNT).")

    companion object {

        /**
         * The most files (songs, setlists, and library files: pictures and attachments) one library export writes, so that
         * it never runs out of a 128 MiB heap, Android's smallest. The archive is written as it is read, but the writer
         * keeps every entry's name, offset, CRC, and sizes for the central directory, and the export its listing of the
         * library: about 1.1 KB a file in all, measured. And it reads each entry whole, deflating one that compresses into
         * a buffer of its own size, so the largest attachment (`MAX_ATTACHMENT_FILE_SIZE`, 50 MiB) takes 100 MiB at its
         * turn. In a JVM capped at 128 MiB such an export ran out of the heap past about 18,000 files (past about 66,000
         * where the attachment does not compress, so that only one copy of it is held); 10,000 files take about 11 MB,
         * leaving the rest of the app some 8 MB beside them. A realistic library is a few hundred songs.
         */
        const val MAX_FILE_COUNT = 10_000
    }
}
