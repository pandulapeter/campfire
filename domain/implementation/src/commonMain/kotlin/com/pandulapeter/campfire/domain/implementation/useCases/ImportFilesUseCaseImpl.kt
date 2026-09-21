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
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.useCases.ImportFilesUseCase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Factory

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
        val importedSongFileNames = mutableListOf<String>()
        val duplicateFileNames = mutableListOf<String>()
        // Where each imported file ended up, for the setlists below. Only files that held exactly one song are in
        // here: a file that held several has no single name a setlist could have been pointing at.
        val storedSongFileNames = mutableMapOf<String, String>()

        val importedSetlistFileNames = mutableListOf<String>()
        try {
            // Where each song of the plan ended up, by its place in the plan: a repeat of an earlier song of the batch
            // is wherever that one went, which was not known when the plan was made.
            val storedNames = arrayOfNulls<String>(plan.songs.size)
            val replacedSongFileNames = mutableSetOf<String>()
            plan.songs.forEachIndexed { index, entry ->
                val storedName = when (val action = entry.action(resolution)) {
                    Action.WRITE, Action.REPLACE -> songRepository.importSong(
                        fileName = entry.fileName,
                        text = entry.text,
                        shouldReplace = action == Action.REPLACE && replacedSongFileNames.add(entry.fileName),
                    ).fileName.also { importedSongFileNames += it }

                    // Already in the library, or already written by this import, so the name it arrived under points there.
                    Action.DISREGARD -> (entry.repeatedEntryIndex?.let(storedNames::getOrNull) ?: entry.fileName).also { duplicateFileNames += it }
                    Action.LEAVE_ALONE -> entry.fileName
                }
                storedNames[index] = storedName
                entry.sourceFileName?.let { storedSongFileNames[it] = storedName }
            }

            var priority = (setlistRepository.loadSetlistsIfNeeded().orEmpty().maxOfOrNull { it.priority } ?: -1) + 1
            val replacedSetlistFileNames = mutableSetOf<String>()
            plan.setlists.forEach { entry ->
                when (val action = entry.action(resolution)) {
                    Action.WRITE, Action.REPLACE -> importedSetlistFileNames += setlistRepository.importSetlist(
                        setlist = entry.setlist.copy(
                            priority = priority++,
                            entries = entry.setlist.entries.map { setlistEntry ->
                                setlistEntry.copy(songFileName = storedSongFileNames[setlistEntry.songFileName] ?: setlistEntry.songFileName)
                            },
                        ),
                        shouldReplace = action == Action.REPLACE && replacedSetlistFileNames.add(entry.fileName),
                    ).fileName

                    Action.DISREGARD -> duplicateFileNames += entry.fileName
                    Action.LEAVE_ALONE -> Unit
                }
            }
        } finally {
            // One read of the directory at the end rather than one cache update per file, which for a big archive
            // would cost more than the import itself. It runs even when a write failed or the import was cancelled
            // halfway, since the files written before that are on disk and would otherwise be missing from the lists
            // until something else rescanned them.
            withContext(NonCancellable) {
                if (importedSongFileNames.isNotEmpty()) {
                    songRepository.rescan()
                }
                if (importedSetlistFileNames.isNotEmpty()) {
                    setlistRepository.rescan()
                }
            }
        }
        return ImportResult(
            importedSongFileNames = importedSongFileNames,
            importedSetlistFileNames = importedSetlistFileNames,
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
