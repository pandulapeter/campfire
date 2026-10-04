/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.importReport

import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.ImportResult
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song

/** One group of files of the import screen's list, under one heading. */
internal data class ImportReportSection(
    val kind: Kind,
    val rows: List<ImportReportRow>,
) {

    /**
     * In the order the screen lists them: what went wrong or was left out first, since that is what the screen was put
     * up for, and what arrived as expected after it.
     */
    enum class Kind {
        CONFLICTS,
        FAILED,
        UNPROCESSED,
        SKIPPED_CONFLICTS,
        SKIPPED,
        OVERSIZED,
        UNREADABLE,
        SONGS,
        SETLISTS,
        DUPLICATES,
    }
}

/**
 * One file of an [ImportReportSection].
 *
 * @param key Unique within its section, which the file name alone is not: a batch can bring the same file twice.
 * @param title What the library calls the song or setlist the file became, where it holds one under that name.
 * @param subtitle The artist of that song.
 * @param isSong Whether [fileName] is a song of the library, which is what can be opened from the row.
 */
internal data class ImportReportRow(
    val key: String,
    val fileName: String,
    val title: String? = null,
    val subtitle: String? = null,
    val isSong: Boolean = false,
    val isConverted: Boolean = false,
)

/** The names a conflict question is about. */
internal fun importReportSections(summary: ImportPlan.Summary) = listOf(
    ImportReportSection(ImportReportSection.Kind.CONFLICTS, summary.conflictingFileNames.toRows()),
).filter { it.rows.isNotEmpty() }

/**
 * What an import came to, every group that has any files in it. Songs and setlists are named the way the library
 * names them, read from [songs] and [setlists] as they are now: a file the import wrote and something outside the app
 * has removed since (a sync run) is listed by its file name, and cannot be opened. What the app itself deletes or
 * renames is followed in the result instead, see [followingLibraryFileNames].
 */
internal fun importReportSections(
    result: ImportResult,
    songs: List<Song>,
    setlists: List<Setlist>,
): List<ImportReportSection> {
    val songsByFileName = songs.associateBy { it.fileName }
    val setlistsByFileName = setlists.associateBy { it.fileName }
    val converted = result.convertedSongFileNames.toSet()
    fun List<String>.toSongRows() = toRows { fileName ->
        val song = songsByFileName[fileName]
        ImportReportRow(
            key = fileName,
            fileName = fileName,
            title = song?.title,
            subtitle = song?.artist?.takeIf { it.isNotBlank() },
            isSong = song != null,
            isConverted = fileName in converted,
        )
    }
    return listOf(
        ImportReportSection(ImportReportSection.Kind.FAILED, result.failedFileNames.toRows()),
        ImportReportSection(ImportReportSection.Kind.UNPROCESSED, result.unprocessedFileNames.toRows()),
        ImportReportSection(ImportReportSection.Kind.SKIPPED_CONFLICTS, result.skippedConflictingFileNames.toRows()),
        ImportReportSection(ImportReportSection.Kind.SKIPPED, result.skippedFileNames.toRows()),
        ImportReportSection(ImportReportSection.Kind.OVERSIZED, result.oversizedFileNames.toRows()),
        ImportReportSection(ImportReportSection.Kind.UNREADABLE, result.unreadableDocumentFileNames.toRows()),
        ImportReportSection(ImportReportSection.Kind.SONGS, result.importedSongFileNames.toSongRows()),
        ImportReportSection(
            ImportReportSection.Kind.SETLISTS,
            result.importedSetlistFileNames.toRows { fileName ->
                ImportReportRow(key = fileName, fileName = fileName, title = setlistsByFileName[fileName]?.title)
            },
        ),
        // A duplicate is a song or a setlist the library already had, so it is named like one.
        ImportReportSection(
            ImportReportSection.Kind.DUPLICATES,
            result.duplicateFileNames.toRows { fileName ->
                songsByFileName[fileName]?.let { song ->
                    ImportReportRow(
                        key = fileName,
                        fileName = fileName,
                        title = song.title,
                        subtitle = song.artist.takeIf { it.isNotBlank() },
                        isSong = true,
                    )
                } ?: ImportReportRow(key = fileName, fileName = fileName, title = setlistsByFileName[fileName]?.title)
            },
        ),
    ).filter { it.rows.isNotEmpty() }
}

/**
 * The result with every library file it names passed through [fileName]: the new name of a file that was renamed, or
 * null for one that was deleted, which then leaves the result. Only the names of songs and setlists the import put in
 * the library are touched; what it left out never became a library file, so nothing the library does can change it.
 */
internal fun ImportResult.followingLibraryFileNames(fileName: (String) -> String?) = copy(
    importedSongFileNames = importedSongFileNames.mapNotNull(fileName),
    importedSetlistFileNames = importedSetlistFileNames.mapNotNull(fileName),
    duplicateFileNames = duplicateFileNames.mapNotNull(fileName),
    convertedSongFileNames = convertedSongFileNames.mapNotNull(fileName),
    convertedSongToOpen = convertedSongToOpen?.let(fileName),
)

/**
 * The sections with only the rows whose file name, title or artist holds [query], both folded by [normalize] the way
 * the library's searches fold, so that case, accents, spaces and punctuation do not count, and without the sections
 * that are left with none. A query that folds to nothing is no filter at all.
 */
internal fun List<ImportReportSection>.matching(query: String, normalize: (String) -> String): List<ImportReportSection> {
    val normalizedQuery = normalize(query)
    // A query that folds to nothing (punctuation, symbols or emoji alone) is no search, as on the library's screens.
    if (normalizedQuery.isEmpty()) return this
    return mapNotNull { section ->
        section.copy(
            rows = section.rows.filter { row ->
                listOfNotNull(row.fileName, row.title, row.subtitle).any { normalizedQuery in normalize(it) }
            },
        ).takeIf { it.rows.isNotEmpty() }
    }
}

/** Numbers the second and every later occurrence of a name in its key, so that each row has one of its own. */
private fun List<String>.toRows(
    row: (String) -> ImportReportRow = { ImportReportRow(key = it, fileName = it) },
): List<ImportReportRow> {
    val occurrences = mutableMapOf<String, Int>()
    return map { fileName ->
        val occurrence = occurrences.getOrElse(fileName) { 0 }
        occurrences[fileName] = occurrence + 1
        row(fileName).let { if (occurrence == 0) it else it.copy(key = "${it.key}#$occurrence") }
    }
}
