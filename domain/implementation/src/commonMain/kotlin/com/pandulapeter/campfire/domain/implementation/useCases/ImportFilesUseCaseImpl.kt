/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.ImportConflictResolution
import com.pandulapeter.campfire.data.model.domain.ImportProgress
import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.ImportResult
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.normalizedToNfc
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.useCases.ImportFilesUseCase
import com.pandulapeter.campfire.domain.implementation.ImportPlanner
import com.pandulapeter.campfire.domain.implementation.ImportPlanner.withSongFileNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.core.annotation.Factory
import kotlin.time.Clock

@Factory
class ImportFilesUseCaseImpl internal constructor(
    private val songRepository: SongRepository,
    private val setlistRepository: SetlistRepository,
) : ImportFilesUseCase {

    /**
     * Songs first, setlists second: a setlist points at songs by file name, and a song that had to be renamed to
     * avoid a collision has to be followed to its new name before the setlist next to it in the archive is written.
     */
    override suspend operator fun invoke(
        plan: ImportPlan,
        resolution: ImportConflictResolution,
        onProgress: (ImportProgress) -> Unit,
    ): ImportResult {
        val importedSongs = mutableListOf<Song>()
        val duplicateFileNames = mutableListOf<String>()
        val convertedSongFileNames = mutableListOf<String>()
        // Where each imported file ended up, for the setlists below. Only files that held exactly one song are in
        // here: a file that held several has no single name a setlist could have been pointing at.
        val storedSongFileNames = mutableMapOf<String, String>()

        val importedSetlists = mutableListOf<Setlist>()
        val skippedConflicts = mutableListOf<String>()
        val total = plan.songs.size + plan.setlists.size
        var processedSongs = 0
        var processedSetlists = 0
        var currentFileName: String? = null
        var isFailed = false
        var failedFileNames = emptyList<String>()
        var unprocessedFileNames = emptyList<String>()
        try {
            // Where each song of the plan ended up, by its place in the plan: a repeat of an earlier song of the batch
            // is wherever that one went, which was not known when the plan was made.
            val storedNames = arrayOfNulls<String>(plan.songs.size)
            // The songs of the plan the answer left out, by their place in it: a repeat of one of them is left out with it
            // rather than being the duplicate of the different library song whose name they both wanted.
            val leftAloneIndices = mutableSetOf<Int>()
            val replacedSongFileNames = mutableSetOf<String>()
            // The planner never asks about a name whose library file this import brings back unchanged; held here too,
            // since this is the one place anything is overwritten and the song lost would be one the import carries.
            val keptSongFileNames = plan.songs
                .filter { it.status == ImportPlan.Status.IDENTICAL && it.repeatedEntryIndex == null }
                .mapTo(hashSetOf()) { it.fileName }
            // A plan is held against the library as it was when the question was asked; a sync run may have deleted, since
            // then, the file an identical song was going to be left as. The names come from the same list the planner used,
            // folded the way the storage layer compares names, so a file another device re-spelt in case only is still there.
            val libraryNames = if (plan.songs.any { it.isIdenticalToLibraryFile }) {
                songRepository.loadSongsIfNeeded()?.mapTo(hashSetOf()) { it.fileName.normalizedToNfc().lowercase() }
            } else {
                null
            }
            plan.songs.forEachIndexed { index, entry ->
                currentFileName = entry.sourceFileName ?: entry.fileName
                onProgress(ImportProgress(ImportProgress.Phase.IMPORTING, processedSongs, total, currentFileName))
                yield()
                val isLibraryFileGone = entry.isIdenticalToLibraryFile && libraryNames != null &&
                    entry.fileName.normalizedToNfc().lowercase() !in libraryNames
                val storedName = when (val action = if (isLibraryFileGone) Action.WRITE else entry.action(resolution)) {
                    Action.WRITE, Action.REPLACE -> {
                        // A replacement goes over the file as the library lists it; anything else is written under the
                        // name the app gives the song, which the storage layer numbers if it is taken. An identical
                        // song's name is the library file it matched, which may be a numbered sibling or a name of an
                        // older rule, so one written because that file has gone is named by its own header, the way the
                        // preparation named it.
                        val replacedFileName = entry.replacesFileName ?: entry.fileName
                        val shouldReplace = action == Action.REPLACE && replacedFileName !in keptSongFileNames &&
                            replacedSongFileNames.add(replacedFileName)
                        songRepository.importSong(
                            fileName = when {
                                shouldReplace -> replacedFileName
                                isLibraryFileGone -> songRepository.importFileName(
                                    fallbackTitle = entry.sourceFileName?.substringBeforeLast('.').orEmpty(),
                                    text = entry.text,
                                )

                                else -> entry.fileName
                            },
                            text = entry.text,
                            shouldReplace = shouldReplace,
                        ).also {
                            importedSongs += it
                            if (entry.isConverted) convertedSongFileNames += it.fileName
                        }.fileName
                    }

                    // Already in the library, or already written by this import, so the name it arrived under points there.
                    Action.DISREGARD -> (entry.repeatedEntryIndex?.let(storedNames::getOrNull) ?: entry.fileName).also {
                        if (entry.repeatedEntryIndex in leftAloneIndices) {
                            leftAloneIndices += index
                            skippedConflicts += entry.fileName
                        } else {
                            duplicateFileNames += it
                        }
                    }

                    Action.LEAVE_ALONE -> entry.fileName.also {
                        leftAloneIndices += index
                        skippedConflicts += it
                    }
                }
                storedNames[index] = storedName
                entry.sourceFileName?.let { storedSongFileNames[it] = storedName }
                processedSongs++
                currentFileName = null
            }

            val librarySetlists = setlistRepository.loadSetlistsIfNeeded().orEmpty()
            // A setlist that arrives naming no day of its own is dated the way a new one is, by the day it was created
            // here. The bundled demo setlist is one of them, so it is dated by the first run that plants it.
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val replacedSetlistFileNames = mutableSetOf<String>()
            // Planned again on the names the songs actually got: a song kept next to the one it collided with is
            // numbered, and a setlist pointing at it is then no longer the library's setlist it was the same as.
            val setlists = ImportPlanner.replanSetlists(
                planned = plan.setlists,
                librarySetlists = librarySetlists,
                songFileNames = storedSongFileNames,
            )
            val keptSetlistFileNames = setlists.filter { it.status == ImportPlan.Status.IDENTICAL }.mapTo(hashSetOf()) { it.fileName }
            plan.setlists.zip(setlists).forEach { (planned, entry) ->
                currentFileName = planned.sourceFileName
                onProgress(ImportProgress(ImportProgress.Phase.IMPORTING, processedSongs + processedSetlists, total, currentFileName))
                yield()
                // A setlist the question was not about goes in numbered rather than being replaced or left out by it.
                val action = if (entry.status == ImportPlan.Status.CONFLICTING && planned.status != ImportPlan.Status.CONFLICTING) {
                    Action.WRITE
                } else {
                    entry.action(resolution)
                }
                when (action) {
                    Action.WRITE, Action.REPLACE -> {
                        val shouldReplace = action == Action.REPLACE && entry.fileName !in keptSetlistFileNames &&
                            replacedSetlistFileNames.add(entry.fileName)
                        // An undated file that replaces a library setlist says nothing about its day or its countdown, so
                        // both stay what the library had. One that is written numbered instead is a new setlist, dated as
                        // one.
                        val replaced = if (shouldReplace) librarySetlists.firstOrNull { it.fileName == entry.fileName } else null
                        importedSetlists += setlistRepository.importSetlist(
                            setlist = entry.setlist.withSongFileNames(storedSongFileNames).let {
                                it.copy(
                                    date = it.date ?: replaced?.date ?: today,
                                    isCountdownShown = if (it.date == null && replaced != null) replaced.isCountdownShown else it.isCountdownShown,
                                )
                            },
                            shouldReplace = shouldReplace,
                        )
                    }

                    Action.DISREGARD -> duplicateFileNames += entry.fileName
                    Action.LEAVE_ALONE -> skippedConflicts += entry.fileName
                }
                processedSetlists++
                currentFileName = null
            }
            onProgress(ImportProgress(ImportProgress.Phase.IMPORTING, total, total))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            // Stop at the first write failure: setlists must never be remapped to songs that did not reach storage.
            println("Could not import ${currentFileName ?: "the batch"}: ${exception.message}")
            isFailed = true
            failedFileNames = listOfNotNull(currentFileName)
            val remaining = plan.songs.drop(processedSongs).map { it.sourceFileName ?: it.fileName } +
                plan.setlists.drop(processedSetlists).map { it.sourceFileName }
            unprocessedFileNames = if (currentFileName == null) remaining else remaining.drop(1)
        } finally {
            // One change to each list at the end rather than one per file, each of which would rebuild the lists
            // downstream, and no read of the directory at all: every file written is in hand as what it became. It runs
            // even when a write failed or the import was cancelled halfway, since the files written before that are on
            // disk and would otherwise be missing from the lists until something else rescanned them.
            onProgress(ImportProgress(ImportProgress.Phase.FINISHING))
            withContext(NonCancellable) {
                songRepository.adoptImported(importedSongs)
                setlistRepository.adoptImported(importedSetlists)
            }
        }
        return ImportResult(
            importedSongFileNames = importedSongs.map { it.fileName },
            importedSetlistFileNames = importedSetlists.map { it.fileName },
            skippedFileNames = plan.skippedFileNames,
            skippedConflictingFileNames = skippedConflicts,
            isFailed = isFailed,
            failedFileNames = failedFileNames,
            unprocessedFileNames = unprocessedFileNames,
            duplicateFileNames = duplicateFileNames,
            oversizedFileNames = plan.oversizedFileNames,
            unreadableDocumentFileNames = plan.unreadableDocumentFileNames,
            convertedSongFileNames = convertedSongFileNames,
            convertedSongToOpen = convertedSongFileNames.singleOrNull()?.takeIf {
                !isFailed && plan.songs.size == 1 && plan.setlists.isEmpty() && plan.skippedFileNames.isEmpty() &&
                    plan.oversizedFileNames.isEmpty() && plan.unreadableDocumentFileNames.isEmpty()
            },
        )
    }

    /** What one planned file turns into once the answer to the conflicts is known. */
    private enum class Action {
        WRITE,
        REPLACE,
        DISREGARD,
        LEAVE_ALONE,
    }

    private fun ImportPlan.SongEntry.action(resolution: ImportConflictResolution) = action(status, resolution)

    /** An identical song that matched a library file rather than an earlier song of the same batch. */
    private val ImportPlan.SongEntry.isIdenticalToLibraryFile get() = status == ImportPlan.Status.IDENTICAL && repeatedEntryIndex == null

    private fun ImportPlan.SetlistEntry.action(resolution: ImportConflictResolution) = action(status, resolution)

    private fun action(status: ImportPlan.Status, resolution: ImportConflictResolution) = when (status) {
        ImportPlan.Status.NEW -> Action.WRITE
        ImportPlan.Status.IDENTICAL -> Action.DISREGARD
        ImportPlan.Status.CONFLICTING -> when (resolution) {
            // The name is taken, so writing under it is what produces the "_2" the storage layer suffixes — which is
            // also how a NEW entry whose name an earlier file of the same import took gets its number.
            ImportConflictResolution.KEEP_BOTH -> Action.WRITE
            ImportConflictResolution.REPLACE -> Action.REPLACE
            ImportConflictResolution.SKIP -> Action.LEAVE_ALONE
        }
    }
}
