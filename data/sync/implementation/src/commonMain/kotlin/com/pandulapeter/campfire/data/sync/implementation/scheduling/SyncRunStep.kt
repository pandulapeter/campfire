/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.scheduling

import com.pandulapeter.campfire.data.source.remote.api.sync.SyncFolder
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteSharedFile
import com.pandulapeter.campfire.data.sync.implementation.SyncIndexEntry
import com.pandulapeter.campfire.data.sync.implementation.SyncKey

/**
 * Something an app keeps in the cloud folder beside its library files, brought up to date by every run that completed,
 * once the files are: [SyncRunner] runs each of [SyncRunSteps] in order, inside the run lock, before the run's index is
 * saved.
 */
internal interface SyncRunStep {

    /** Names the step in `SyncSummary.failedSteps` and the log, never anything of the user's. */
    val id: String

    /**
     * The folder's own documents only this step reads or writes. Another device changing one starts no run, which nothing
     * the run settles depends on; [onDocumentChanged] reads it instead.
     */
    val documentNames: Set<String>

    /**
     * Brings the step's part of the folder up to date with the library the run left, and answers whether all of it was.
     * A `SyncRunEndingException` ends the run with its reason; any other exception, like false, is the step's failure
     * alone, reported in `SyncSummary.failedSteps`, and the next run tries again.
     *
     * @param index The run's index as it completed.
     * @param documentRevisions The folder's documents' revisions, as the run's last listing named them.
     * @param sharedFiles The shared copies, as the run's last listing named them.
     */
    suspend fun run(
        folder: SyncFolder,
        index: Map<SyncKey, SyncIndexEntry>,
        documentRevisions: Map<String, String>,
        sharedFiles: List<RemoteSharedFile>,
    ): Boolean

    /** Reads one of [documentNames] that another device changed while the app is in front, outside any run. */
    suspend fun onDocumentChanged(folder: SyncFolder)

    /**
     * Forgets what the step keeps on this device about the folder, which describes it as the index does and goes with it:
     * on disconnecting, and on connecting another account. Runs under the run lock there, except where a previous
     * installation's connection is forgotten at start up, when no run can be going.
     */
    suspend fun forget()
}
