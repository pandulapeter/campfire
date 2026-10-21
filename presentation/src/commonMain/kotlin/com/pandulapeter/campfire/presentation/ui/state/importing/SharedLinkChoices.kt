/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportConflictResolution
import com.pandulapeter.campfire.data.model.importing.ImportEntryKey
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.model.library.LibraryFiles

/**
 * What a shared link's review starts on: Replace for every taken name that an earlier import of the same link wrote on
 * this device ([recorded], library paths as `GetSharedLinkImportUseCase` answers them), since opening a link again is
 * how its recipient takes the sharer's changes, and Keep both, as in any import, for every other one. A link from anyone
 * may hold a song named like one of the recipient's own, and neither the review nor the confirmation tells the two
 * apart, so a name the link never wrote here is never offered up to it. Set as the rows' own choices, which the choice
 * for all of them still overrides.
 */
internal fun sharedLinkChoices(plan: ImportPlan, recorded: Set<String>) = ImportChoices(
    conflictResolutions = buildMap {
        plan.songs.forEachIndexed { index, entry ->
            if (entry.status == ImportPlan.Status.CONFLICTING && recorded.containsName(LibraryFileKind.SONG, entry.replacesFileName ?: entry.fileName)) {
                put(ImportEntryKey(ImportEntryKey.Kind.SONG, index), ImportConflictResolution.REPLACE)
            }
        }
        plan.setlists.forEachIndexed { index, entry ->
            if (entry.status == ImportPlan.Status.CONFLICTING && recorded.containsName(LibraryFileKind.SETLIST, entry.fileName)) {
                put(ImportEntryKey(ImportEntryKey.Kind.SETLIST, index), ImportConflictResolution.REPLACE)
            }
        }
    },
)

/**
 * The songs of [result] a shared link's import wrote under a name one of [plan]'s entries asked for, which is the name a
 * later import of the link asks for again; a numbered copy kept beside one of the library's is not, and is left out.
 */
internal fun sharedLinkSongFileNamesWrittenBy(plan: ImportPlan, result: ImportResult) =
    result.importedSongFileNames.filter { name -> plan.songs.any { LibraryFiles.isSameLibraryName(it.replacesFileName ?: it.fileName, name) } }

/** The same for the setlists, see [sharedLinkSongFileNamesWrittenBy]. */
internal fun sharedLinkSetlistFileNamesWrittenBy(plan: ImportPlan, result: ImportResult) =
    result.importedSetlistFileNames.filter { name -> plan.setlists.any { LibraryFiles.isSameLibraryName(it.fileName, name) } }

private fun Set<String>.containsName(kind: LibraryFileKind, fileName: String) = any { path ->
    path.substringBefore('/') == kind.id && LibraryFiles.isSameLibraryName(path.substringAfter('/'), fileName)
}
