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

import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination

/**
 * What [CampfireDestination.ImportReport] shows, see [CampfireViewModel.importReport]: the stages of one import the user
 * started, in the order it goes through them, each replacing the one before it in place.
 */
sealed interface ImportReport {

    /** The files being read and compared, or the address being downloaded; [sourceName] names what is read, if anything. */
    data class Loading(val sourceName: String?) : ImportReport

    /** Why nothing could be read, which [CampfireViewModel.retryImport] asks again about. */
    data class Failed(val reason: ImportFailure, val sourceName: String?) : ImportReport

    /**
     * Everything the files hold, each entry ticked or not and each name already taken given its choice, see
     * [CampfireViewModel.confirmImport]. The choices live here rather than on the screen, so that a screen composed
     * again (an Android activity recreated under it) keeps every tick. [isDemoLibrary] is the app's own demo library,
     * which the subtitle names as such rather than by the bundled files it is read from.
     */
    data class Review(val plan: ImportPlan, val choices: ImportChoices, val isDemoLibrary: Boolean) : ImportReport

    /** The choices being carried out, with [CampfireViewModel.importProgress] saying how far. */
    data object Importing : ImportReport

    /** What the import came to; null for one that failed before it could say. */
    data class Finished(val result: ImportResult?) : ImportReport
}

/**
 * Whether the import this report is about is lost with the process: every stage but [ImportReport.Finished], a report
 * still waiting behind the onboarding or a dialog to be shown included.
 */
internal val ImportReport?.isUnfinished get() = this != null && this !is ImportReport.Finished

/** Why [ImportReport.Failed] has nothing to review. */
enum class ImportFailure {
    /** The host could not be reached, did not answer in time, or was busy; worth asking again a minute later. */
    UNREACHABLE,

    /** The address answered that there is nothing there. */
    NOT_FOUND,

    /** The file is larger than an import reads. */
    TOO_LARGE,

    /** The files were in hand, and could not be read. */
    UNREADABLE,
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
