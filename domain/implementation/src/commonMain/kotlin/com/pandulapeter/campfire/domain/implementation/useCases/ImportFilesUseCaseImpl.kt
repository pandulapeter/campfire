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

        plan.songs.forEach { entry ->
            when (entry.action(resolution)) {
                Action.WRITE, Action.REPLACE -> {
                    val song = songRepository.importSong(
                        fileName = entry.fileName,
                        text = entry.text,
                        shouldReplace = entry.action(resolution) == Action.REPLACE,
                    )
                    importedSongFileNames += song.fileName
                    entry.sourceFileName?.let { storedSongFileNames[it] = song.fileName }
                }

                // Already in the library, so the name it arrived under still points at it for the setlists below.
                Action.DISREGARD -> {
                    duplicateFileNames += entry.fileName
                    entry.sourceFileName?.let { storedSongFileNames[it] = entry.fileName }
                }

                Action.LEAVE_ALONE -> entry.sourceFileName?.let { storedSongFileNames[it] = entry.fileName }
            }
        }

        val importedSetlistFileNames = mutableListOf<String>()
        var priority = (setlistRepository.loadSetlistsIfNeeded().orEmpty().maxOfOrNull { it.priority } ?: -1) + 1
        plan.setlists.forEach { entry ->
            when (val action = entry.action(resolution)) {
                Action.WRITE, Action.REPLACE -> importedSetlistFileNames += setlistRepository.importSetlist(
                    setlist = entry.setlist.copy(
                        priority = priority++,
                        entries = entry.setlist.entries.map { setlistEntry ->
                            setlistEntry.copy(songFileName = storedSongFileNames[setlistEntry.songFileName] ?: setlistEntry.songFileName)
                        },
                    ),
                    shouldReplace = action == Action.REPLACE,
                ).fileName

                Action.DISREGARD -> duplicateFileNames += entry.fileName
                Action.LEAVE_ALONE -> Unit
            }
        }

        // One read of the directory at the end rather than one cache update per file, which for a big archive would
        // cost more than the import itself.
        if (importedSongFileNames.isNotEmpty()) {
            songRepository.rescan()
        }
        if (importedSetlistFileNames.isNotEmpty()) {
            setlistRepository.rescan()
        }
        return ImportResult(
            importedSongFileNames = importedSongFileNames,
            importedSetlistFileNames = importedSetlistFileNames,
            skippedFileNames = plan.skippedFileNames,
            duplicateFileNames = duplicateFileNames,
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
            // The name is taken, so writing under it is what produces the " (2)" the storage layer suffixes.
            ImportConflictResolution.KEEP_BOTH -> Action.WRITE
            ImportConflictResolution.REPLACE -> Action.REPLACE
            ImportConflictResolution.SKIP -> Action.LEAVE_ALONE
        }
    }
}
