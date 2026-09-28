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
import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.ImportResult
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.useCases.ImportFilesUseCase
import com.pandulapeter.campfire.domain.implementation.ImportPlanner
import com.pandulapeter.campfire.domain.implementation.ImportPlanner.withSongFileNames
import kotlinx.coroutines.NonCancellable
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
    override suspend operator fun invoke(plan: ImportPlan, resolution: ImportConflictResolution): ImportResult {
        val importedSongs = mutableListOf<Song>()
        val duplicateFileNames = mutableListOf<String>()
        // Where each imported file ended up, for the setlists below. Only files that held exactly one song are in
        // here: a file that held several has no single name a setlist could have been pointing at.
        val storedSongFileNames = mutableMapOf<String, String>()

        val importedSetlists = mutableListOf<Setlist>()
        try {
            // Where each song of the plan ended up, by its place in the plan: a repeat of an earlier song of the batch
            // is wherever that one went, which was not known when the plan was made.
            val storedNames = arrayOfNulls<String>(plan.songs.size)
            val replacedSongFileNames = mutableSetOf<String>()
            // The planner never asks about a name whose library file this import brings back unchanged; held here too,
            // since this is the one place anything is overwritten and the song lost would be one the import carries.
            val keptSongFileNames = plan.songs
                .filter { it.status == ImportPlan.Status.IDENTICAL && it.repeatedEntryIndex == null }
                .mapTo(hashSetOf()) { it.fileName }
            plan.songs.forEachIndexed { index, entry ->
                val storedName = when (val action = entry.action(resolution)) {
                    Action.WRITE, Action.REPLACE -> {
                        // A replacement goes over the file as the library lists it; anything else is written under the
                        // name the app gives the song, which the storage layer numbers if it is taken.
                        val replacedFileName = entry.replacesFileName ?: entry.fileName
                        val shouldReplace = action == Action.REPLACE && replacedFileName !in keptSongFileNames &&
                            replacedSongFileNames.add(replacedFileName)
                        songRepository.importSong(
                            fileName = if (shouldReplace) replacedFileName else entry.fileName,
                            text = entry.text,
                            shouldReplace = shouldReplace,
                        ).also { importedSongs += it }.fileName
                    }

                    // Already in the library, or already written by this import, so the name it arrived under points there.
                    Action.DISREGARD -> (entry.repeatedEntryIndex?.let(storedNames::getOrNull) ?: entry.fileName).also { duplicateFileNames += it }
                    Action.LEAVE_ALONE -> entry.fileName
                }
                storedNames[index] = storedName
                entry.sourceFileName?.let { storedSongFileNames[it] = storedName }
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
                        // An undated file that replaces a library setlist says nothing about its day, so the day stays the
                        // one the library had. One that is written numbered instead is a new setlist, dated as one.
                        val fallbackDate = if (shouldReplace) librarySetlists.firstOrNull { it.fileName == entry.fileName }?.date else null
                        importedSetlists += setlistRepository.importSetlist(
                            setlist = entry.setlist.withSongFileNames(storedSongFileNames).let { it.copy(date = it.date ?: fallbackDate ?: today) },
                            shouldReplace = shouldReplace,
                        )
                    }

                    Action.DISREGARD -> duplicateFileNames += entry.fileName
                    Action.LEAVE_ALONE -> Unit
                }
            }
        } finally {
            // One change to each list at the end rather than one per file, each of which would rebuild the lists
            // downstream, and no read of the directory at all: every file written is in hand as what it became. It runs
            // even when a write failed or the import was cancelled halfway, since the files written before that are on
            // disk and would otherwise be missing from the lists until something else rescanned them.
            withContext(NonCancellable) {
                songRepository.adoptImported(importedSongs)
                setlistRepository.adoptImported(importedSetlists)
            }
        }
        return ImportResult(
            importedSongFileNames = importedSongs.map { it.fileName },
            importedSetlistFileNames = importedSetlists.map { it.fileName },
            skippedFileNames = plan.skippedFileNames,
            duplicateFileNames = duplicateFileNames,
            oversizedFileNames = plan.oversizedFileNames,
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
