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

import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.data.model.importing.StagedFileMissingException
import com.pandulapeter.campfire.data.model.importing.sharedLinkChoices
import com.pandulapeter.campfire.presentation.ui.firstRun.DemoLibrary
import com.pandulapeter.campfire.presentation.ui.platform.SharedTextTitle
import kotlinx.coroutines.CompletableDeferred

/**
 * One batch in the import queue ([ImportWork.queue]), as [ImportController] takes it.
 *
 * @param source Emptied once a preparation has succeeded: the plan carries everything the rest of the import needs,
 *   while the request lives for as long as the review does. Kept while a preparation failed, for Try again, and an
 *   address that was downloaded becomes the file it downloaded (a shared link keeps it beside the link), so a read that
 *   failed is not downloaded again.
 * @param shouldAnnounceResult False for the import nobody asked for: the demo library planted on a first run is
 *   the library the user is about to be shown, and a snackbar or a result counting the files of it would be the app
 *   reporting on something that, as far as anyone can tell, simply came with it.
 * @param shouldOpenSong True for files the system handed over, for an address, and for a shared link, see [ImportController.importFiles].
 * @param shouldReview See [ImportController.enqueueImport].
 * @param isDemoLibrary The files are [DemoLibrary]'s, and what of them is written is remembered for sync.
 * @param isNewSongFromText The one file is a text pasted or dropped as a new song, see [ImportController.enqueueImport].
 * @param suggestion What the new song is titled for what its text does not declare, see [ImportController.enqueueImport].
 * @param sharedLinkFileId The file id of the shared link the batch is, kept past [source] being emptied: what the import
 *   writes under its own names is remembered for it, see [sharedLinkChoices].
 */
internal class ImportRequest(
    var source: ImportSource,
    val shouldAnnounceResult: Boolean,
    val shouldOpenSong: Boolean,
    val shouldReview: Boolean = true,
    val isDemoLibrary: Boolean = false,
    val isNewSongFromText: Boolean = false,
    val suggestion: SharedTextTitle? = null,
    val sharedLinkFileId: String? = null,
    val settled: CompletableDeferred<Unit> = CompletableDeferred(),
) {

    /**
     * Every staged file the batch was read from or planned with (a received or downloaded archive, and the pictures and
     * attachments unpacked from it), whose staging requests are deleted once the batch has settled for good
     * (`ImportWork.settle`), never before: Try again reads the same staged archive.
     */
    val stagedFiles = mutableSetOf<StagedFile>()

    /**
     * What of [source] was staged, kept once [source] is emptied after a successful preparation: an Import that finds a
     * staged file gone fails before writing anything, and Try again reads this again.
     */
    private var stagedSource: ImportSource? = null

    /** Empties [source] once its plan is made, keeping what of it was staged ([stagedSource]). */
    fun forgetSource() {
        stagedSource = source.stagedPart
        source = ImportSource.Files(emptyList())
    }

    /**
     * The failure stage an Import that threw [exception] turns into, its Try again reading the batch again, or null where
     * the failure is not that: nothing was written, and the plan names a staged file that is gone
     * ([StagedFileMissingException]), so reading again what is still staged is the one way back.
     */
    fun readAgainAfter(exception: Exception): ImportReport.Failed? {
        val staged = stagedSource?.takeIf { exception is StagedFileMissingException } ?: return null
        source = staged
        return ImportReport.Failed(ImportFailure.UNREADABLE, staged.name, staged.fileCount)
    }
}
