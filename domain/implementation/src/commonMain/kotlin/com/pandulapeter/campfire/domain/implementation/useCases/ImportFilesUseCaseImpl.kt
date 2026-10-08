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
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.useCases.ImportFilesUseCase
import com.pandulapeter.campfire.domain.implementation.ImportPlanner
import com.pandulapeter.campfire.domain.implementation.ImportPlanner.withSongFileNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Factory

@Factory
class ImportFilesUseCaseImpl internal constructor(
    private val songRepository: SongRepository,
    private val setlistRepository: SetlistRepository,
    private val logger: Logger,
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
        val run = ImportRun(plan = plan, resolution = resolution, onProgress = onProgress)
        try {
            run.writeSongs()
            run.writeSetlists()
            onProgress(ImportProgress(ImportProgress.Phase.IMPORTING, run.total, run.total))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            // Stop at the first write failure: setlists must never be remapped to songs that did not reach storage.
            run.failAt(exception)
        } finally {
            // One change to each list at the end rather than one per file, each of which would rebuild the lists
            // downstream, and no read of the directory at all: every file written is in hand as what it became. It runs
            // even when a write failed or the import was cancelled halfway, since the files written before that are on
            // disk and would otherwise be missing from the lists until something else rescanned them.
            onProgress(ImportProgress(ImportProgress.Phase.FINISHING))
            withContext(NonCancellable) {
                songRepository.adoptImported(run.importedSongs)
                setlistRepository.adoptImported(run.importedSetlists)
            }
        }
        return run.result()
    }

    /** One import being written: what has been written, left out and reached so far, which the result is made of. */
    private inner class ImportRun(
        private val plan: ImportPlan,
        private val resolution: ImportConflictResolution,
        private val onProgress: (ImportProgress) -> Unit,
    ) {
        val importedSongs = mutableListOf<Song>()
        private val duplicateFileNames = mutableListOf<String>()
        private val convertedSongFileNames = mutableListOf<String>()

        /**
         * Where each imported file ended up, for the setlists. Only files that held exactly one song are in here: a file
         * that held several has no single name a setlist could have been pointing at. Kept per picked file the song came
         * out of, since names are only unique within one archive.
         */
        private val storedSongFileNamesByOrigin = mutableMapOf<Int?, MutableMap<String, String>>()

        val importedSetlists = mutableListOf<Setlist>()
        private val skippedConflicts = mutableListOf<String>()
        val total = plan.songs.size + plan.setlists.size
        private var processedSongs = 0
        private var processedSetlists = 0
        private var currentFileName: String? = null
        private var isFailed = false
        private var failedFileNames = emptyList<String>()
        private var unprocessedFileNames = emptyList<String>()

        suspend fun writeSongs() {
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
            // folded the way the storage layer compares names (LibraryFiles.identityKey), so a file another device re-spelt in
            // case only is still there.
            val libraryNames = if (plan.songs.any { it.isIdenticalToLibraryFile }) {
                songRepository.loadSongsIfNeeded()?.mapTo(hashSetOf()) { LibraryFiles.identityKey(it.fileName) }
            } else {
                null
            }
            plan.songs.forEachIndexed { index, entry ->
                currentFileName = entry.sourceFileName ?: entry.fileName
                onProgress(ImportProgress(ImportProgress.Phase.IMPORTING, processedSongs, total, currentFileName))
                yield()
                val isLibraryFileGone = entry.isIdenticalToLibraryFile && libraryNames != null &&
                    LibraryFiles.identityKey(entry.fileName) !in libraryNames
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
                            skippedConflicts += it
                        } else {
                            duplicateFileNames += it
                        }
                    }

                    // The library's spelling of the name where it holds the file under another one (case, Unicode form),
                    // since that is the name the song list holds and the batch's setlists are pointed at.
                    Action.LEAVE_ALONE -> (entry.replacesFileName ?: entry.fileName).also {
                        leftAloneIndices += index
                        skippedConflicts += it
                    }
                }
                storedNames[index] = storedName
                entry.sourceFileName?.let { storedSongFileNamesByOrigin.getOrPut(entry.origin) { mutableMapOf() }[it] = storedName }
                processedSongs++
                currentFileName = null
            }
        }

        suspend fun writeSetlists() {
            val librarySetlists = setlistRepository.loadSetlistsIfNeeded().orEmpty()
            val replacedSetlistFileNames = mutableSetOf<String>()
            // Planned again on the names the songs actually got: a song kept next to the one it collided with is
            // numbered, and a setlist pointing at it is then no longer the library's setlist it was the same as.
            val storedSongFileNames = ImportPlanner.SongFileNames(storedSongFileNamesByOrigin)
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
                        // A setlist that arrived naming no day of its own carries the day it was imported on, the way a
                        // new one carries the day it was created here. Replacing a library setlist, it says nothing about
                        // that one's day or countdown, so both stay what the library had; written numbered instead, it is
                        // a new setlist and keeps the import's day.
                        val replaced = if (shouldReplace) librarySetlists.firstOrNull { it.fileName == entry.fileName } else null
                        importedSetlists += setlistRepository.importSetlist(
                            setlist = entry.setlist.withSongFileNames(storedSongFileNames.forOrigin(planned.origin)).let {
                                if (planned.isDated || replaced == null) it else it.copy(date = replaced.date, isCountdownShown = replaced.isCountdownShown)
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
        }

        fun failAt(exception: Exception) {
            logger.log("Could not import ${currentFileName ?: "the batch"}: ${exception.message}")
            isFailed = true
            failedFileNames = listOfNotNull(currentFileName)
            val remaining = plan.songs.drop(processedSongs).map { it.sourceFileName ?: it.fileName } +
                plan.setlists.drop(processedSetlists).map { it.sourceFileName }
            unprocessedFileNames = if (currentFileName == null) remaining else remaining.drop(1)
        }

        fun result() = ImportResult(
            importedSongFileNames = importedSongs.map { it.fileName },
            importedSetlistFileNames = importedSetlists.map { it.fileName },
            skippedFileNames = plan.skippedFileNames,
            // Both lists name library files and are filled once per incoming entry, so a song the batch brings twice
            // would be named twice; and a repeat of a song this import wrote was not in the library before it.
            skippedConflictingFileNames = skippedConflicts.distinct(),
            isFailed = isFailed,
            failedFileNames = failedFileNames,
            unprocessedFileNames = unprocessedFileNames,
            duplicateFileNames = duplicateFileNames.distinct() - importedSongs.mapTo(hashSetOf()) { it.fileName },
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
