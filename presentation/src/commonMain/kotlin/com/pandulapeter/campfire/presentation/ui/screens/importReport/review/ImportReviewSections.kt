/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.importReport.review

import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportReport
import com.pandulapeter.campfire.presentation.ui.screens.importReport.finished.ImportReportSection
import com.pandulapeter.campfire.presentation.ui.screens.importReport.finished.matching
import com.pandulapeter.campfire.presentation.ui.screens.importReport.finished.sections
import androidx.compose.ui.state.ToggleableState
import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportConflictResolution
import com.pandulapeter.campfire.data.model.importing.ImportEntryKey
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.song.Song

/**
 * One group of the review's list, under one heading. A type of its own rather than an [ImportReportSection], so that a
 * row of the review, which may carry a box, can never be handed to the result's list, which has none.
 */
internal data class ImportReviewSection(
    val kind: Kind,
    val rows: List<ImportReviewRow>,
) {

    /** In the order the review lists them: what needs a choice first, then what will arrive, then what will not. */
    enum class Kind {
        CONFLICTS,
        SONGS,
        SETLISTS,
        DUPLICATES,
        SKIPPED,
        OVERSIZED,
        UNREADABLE,
    }
}

/**
 * One entry, or one file left out, of an [ImportReviewSection].
 *
 * @param key The entry of the plan the row ticks; null for a row without a box (already in the library, or a file
 *   that cannot be imported).
 * @param rowKey Unique within the whole list, which neither the file name nor the title is: a batch can bring the same
 *   name twice.
 * @param title What the incoming song's header calls it, or the setlist's title; null where only the file name is known.
 * @param subtitle The incoming song's artist.
 * @param isSong Whether the row is about a song, which is what a library file can be opened from it as.
 * @param libraryFileName The library file the row is about: the one a taken name would be numbered next to or replace,
 *   or the one that already is the entry.
 * @param libraryTitle What the library calls [libraryFileName], where it still holds it.
 * @param repeatCount How many more times the batch brings the same song, each one following this row.
 * @param setlistSongCount For a setlist, how many songs it names.
 * @param setlistSongsInImport For a setlist, how many of those are songs of this import.
 */
internal data class ImportReviewRow(
    val key: ImportEntryKey?,
    val rowKey: String,
    val fileName: String,
    val title: String? = null,
    val subtitle: String? = null,
    val isSong: Boolean = false,
    val isConverted: Boolean = false,
    val libraryFileName: String? = null,
    val libraryTitle: String? = null,
    val repeatCount: Int = 0,
    val setlistSongCount: Int? = null,
    val setlistSongsInImport: Int? = null,
)

/**
 * What a review of [plan] lists, every group that has any rows in it. The library's songs and setlists are named the
 * way [songs] and [setlists] name them now; a repeat of an earlier entry of the batch is not listed at all, since it
 * follows that entry in or out, and the row of that entry counts it instead.
 */
internal fun importReviewSections(
    plan: ImportPlan,
    songs: List<Song>,
    setlists: List<Setlist>,
): List<ImportReviewSection> {
    val songsByFileName = songs.associateBy { it.fileName }
    val setlistsByFileName = setlists.associateBy { it.fileName }
    val repeatCounts = plan.songs.mapNotNull { it.repeatedEntryIndex }.groupingBy { it }.eachCount()
    val incomingSongFileNames = plan.songs.mapNotNullTo(hashSetOf()) { it.sourceFileName }
    fun songRow(kind: ImportReviewSection.Kind, index: Int, entry: ImportPlan.SongEntry, libraryFileName: String?): ImportReviewRow {
        val key = ImportEntryKey(ImportEntryKey.Kind.SONG, index)
        return ImportReviewRow(
            key = key.takeIf { entry.status != ImportPlan.Status.IDENTICAL },
            rowKey = "${kind}_${key.kind}_${key.index}",
            fileName = entry.sourceFileName ?: entry.fileName,
            title = entry.title.takeIf { it.isNotBlank() },
            subtitle = entry.artist.takeIf { it.isNotBlank() },
            isSong = true,
            isConverted = entry.isConverted,
            libraryFileName = libraryFileName,
            libraryTitle = libraryFileName?.let(songsByFileName::get)?.title,
            repeatCount = repeatCounts[index] ?: 0,
        )
    }
    fun setlistRow(kind: ImportReviewSection.Kind, index: Int, entry: ImportPlan.SetlistEntry, libraryFileName: String?): ImportReviewRow {
        val key = ImportEntryKey(ImportEntryKey.Kind.SETLIST, index)
        val entries = entry.setlist.entries
        return ImportReviewRow(
            key = key.takeIf { entry.status != ImportPlan.Status.IDENTICAL },
            rowKey = "${kind}_${key.kind}_${key.index}",
            fileName = entry.sourceFileName,
            title = entry.setlist.title.takeIf { it.isNotBlank() },
            libraryFileName = libraryFileName,
            libraryTitle = libraryFileName?.let(setlistsByFileName::get)?.title,
            setlistSongCount = entries.size,
            setlistSongsInImport = entries.count { it.songFileName in incomingSongFileNames },
        )
    }
    fun songRows(kind: ImportReviewSection.Kind, status: ImportPlan.Status) = plan.songs.mapIndexedNotNull { index, entry ->
        if (entry.status != status || entry.repeatedEntryIndex != null) return@mapIndexedNotNull null
        songRow(
            kind = kind,
            index = index,
            entry = entry,
            libraryFileName = when (status) {
                ImportPlan.Status.NEW -> null
                ImportPlan.Status.CONFLICTING -> entry.replacesFileName ?: entry.fileName
                ImportPlan.Status.IDENTICAL -> entry.fileName
            },
        )
    }
    fun setlistRows(kind: ImportReviewSection.Kind, status: ImportPlan.Status) = plan.setlists.mapIndexedNotNull { index, entry ->
        if (entry.status != status) return@mapIndexedNotNull null
        setlistRow(kind = kind, index = index, entry = entry, libraryFileName = entry.fileName.takeIf { status != ImportPlan.Status.NEW })
    }
    return listOf(
        ImportReviewSection(
            ImportReviewSection.Kind.CONFLICTS,
            songRows(ImportReviewSection.Kind.CONFLICTS, ImportPlan.Status.CONFLICTING) +
                setlistRows(ImportReviewSection.Kind.CONFLICTS, ImportPlan.Status.CONFLICTING),
        ),
        ImportReviewSection(ImportReviewSection.Kind.SONGS, songRows(ImportReviewSection.Kind.SONGS, ImportPlan.Status.NEW)),
        ImportReviewSection(ImportReviewSection.Kind.SETLISTS, setlistRows(ImportReviewSection.Kind.SETLISTS, ImportPlan.Status.NEW)),
        // Named as the library names them, which is the file the import leaves alone and a tap opens.
        ImportReviewSection(
            ImportReviewSection.Kind.DUPLICATES,
            songRows(ImportReviewSection.Kind.DUPLICATES, ImportPlan.Status.IDENTICAL).map { row ->
                val song = row.libraryFileName?.let(songsByFileName::get)
                if (song == null) row else row.copy(title = song.title, subtitle = song.artist.takeIf { it.isNotBlank() })
            } + setlistRows(ImportReviewSection.Kind.DUPLICATES, ImportPlan.Status.IDENTICAL).map { row ->
                row.copy(title = row.libraryTitle ?: row.title)
            },
        ),
        ImportReviewSection(ImportReviewSection.Kind.SKIPPED, plan.skippedFileNames.toReviewRows(ImportReviewSection.Kind.SKIPPED)),
        ImportReviewSection(ImportReviewSection.Kind.OVERSIZED, plan.oversizedFileNames.toReviewRows(ImportReviewSection.Kind.OVERSIZED)),
        ImportReviewSection(ImportReviewSection.Kind.UNREADABLE, plan.unreadableDocumentFileNames.toReviewRows(ImportReviewSection.Kind.UNREADABLE)),
    ).filter { it.rows.isNotEmpty() }
}

/** The rows of files left out for a reason of their own, numbered by occurrence so that each has a key of its own. */
private fun List<String>.toReviewRows(kind: ImportReviewSection.Kind): List<ImportReviewRow> {
    val occurrences = mutableMapOf<String, Int>()
    return map { fileName ->
        val occurrence = occurrences.getOrElse(fileName) { 0 }
        occurrences[fileName] = occurrence + 1
        ImportReviewRow(key = null, rowKey = "${kind}_$fileName#$occurrence", fileName = fileName)
    }
}

/** The sections with only the rows whose file name, title or artist holds [query], as [matching] narrows the result's. */
internal fun List<ImportReviewSection>.matchingReview(query: String, normalize: (String) -> String): List<ImportReviewSection> {
    val normalizedQuery = normalize(query)
    if (normalizedQuery.isEmpty()) return this
    return mapNotNull { section ->
        section.copy(
            rows = section.rows.filter { row ->
                listOfNotNull(row.fileName, row.title, row.subtitle).any { normalizedQuery in normalize(it) }
            },
        ).takeIf { it.rows.isNotEmpty() }
    }
}

/** What Import would do with [ImportReport.Review]'s ticks as they stand, which the summary card says. */
internal data class ImportReviewSummary(
    val songCount: Int,
    val setlistCount: Int,
    val replacedCount: Int,
    val numberedCount: Int,
    val alreadyThereCount: Int,
    val leftOutCount: Int,
    val skippedCount: Int,
    val oversizedCount: Int,
    val unreadableCount: Int,
) {

    /** How many files Import writes, which its button says. */
    val importCount get() = songCount + setlistCount
}

internal fun importReviewSummary(plan: ImportPlan, choices: ImportChoices): ImportReviewSummary {
    var songCount = 0
    var setlistCount = 0
    var replacedCount = 0
    var numberedCount = 0
    var leftOutCount = 0
    fun count(key: ImportEntryKey, status: ImportPlan.Status, onWritten: () -> Unit) {
        if (status == ImportPlan.Status.IDENTICAL) return
        if (!choices.isChosen(key)) {
            leftOutCount++
            return
        }
        onWritten()
        if (status == ImportPlan.Status.CONFLICTING) {
            if (choices.resolutionOf(key) == ImportConflictResolution.REPLACE) replacedCount++ else numberedCount++
        }
    }
    plan.songs.forEachIndexed { index, entry -> count(ImportEntryKey(ImportEntryKey.Kind.SONG, index), entry.status) { songCount++ } }
    plan.setlists.forEachIndexed { index, entry -> count(ImportEntryKey(ImportEntryKey.Kind.SETLIST, index), entry.status) { setlistCount++ } }
    return ImportReviewSummary(
        songCount = songCount,
        setlistCount = setlistCount,
        replacedCount = replacedCount,
        numberedCount = numberedCount,
        alreadyThereCount = plan.songs.count { it.status == ImportPlan.Status.IDENTICAL && it.repeatedEntryIndex == null } +
            plan.setlists.count { it.status == ImportPlan.Status.IDENTICAL },
        leftOutCount = leftOutCount,
        skippedCount = plan.skippedFileNames.size,
        oversizedCount = plan.oversizedFileNames.size,
        unreadableCount = plan.unreadableDocumentFileNames.size,
    )
}

/** The ticked entries whose name is taken and which are to be written over it, which the confirmation names. */
internal fun replaceCount(plan: ImportPlan, choices: ImportChoices) = importReviewSummary(plan, choices).replacedCount

/** Whether the review of [plan] has anything to tick, which is what an Import button is there for. */
internal val ImportPlan.hasEntriesToChoose
    get() = songs.any { it.status != ImportPlan.Status.IDENTICAL } || setlists.any { it.status != ImportPlan.Status.IDENTICAL }

/**
 * The choice for all of them in the header of the names already taken: what every row of [rows] with a box resolves to,
 * or null where they differ, which the header shows as Mixed. A group with no such row reads the default.
 */
internal fun groupConflictResolution(rows: List<ImportReviewRow>, choices: ImportChoices): ImportConflictResolution? =
    rows.mapNotNull { it.key }.map(choices::resolutionOf).distinct().singleOrNull()
        ?: choices.conflictResolution.takeIf { rows.none { it.key != null } }

/**
 * [resolution] for every row of [keys], the rows the header shows. Unfiltered that is all of them, so it becomes the
 * default and the rows' own choices go; narrowed by a search, only the matching rows are set, by hand.
 */
internal fun ImportChoices.withGroupResolution(
    keys: Collection<ImportEntryKey>,
    isFiltered: Boolean,
    resolution: ImportConflictResolution,
) = if (isFiltered) {
    copy(conflictResolutions = conflictResolutions + keys.associateWith { resolution })
} else {
    copy(conflictResolution = resolution, conflictResolutions = conflictResolutions - keys.toSet())
}

/** The box of a group's header: ticked where every row it shows is, empty where none is, and in between otherwise. */
internal fun groupBoxState(rows: List<ImportReviewRow>, choices: ImportChoices): ToggleableState {
    val keys = rows.mapNotNull { it.key }
    val chosen = keys.count(choices::isChosen)
    return when {
        keys.isEmpty() || chosen == 0 -> ToggleableState.Off
        chosen == keys.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
}
