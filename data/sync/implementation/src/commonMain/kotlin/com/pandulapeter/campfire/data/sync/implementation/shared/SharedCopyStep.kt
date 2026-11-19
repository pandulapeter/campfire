/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.shared

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.library.LibrarySyncSteps
import com.pandulapeter.campfire.data.model.recovering
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncFolder
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteSharedFile
import com.pandulapeter.campfire.data.sync.implementation.SyncIndexEntry
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import com.pandulapeter.campfire.data.sync.implementation.scheduling.SyncRunStep
import org.koin.core.annotation.Single

/**
 * The shared links as a run's step: the copies brought up to date with the library ([SharedCopySync]), the list read
 * again when another device changes it, and the mirror and journal forgotten with the connection.
 */
@Single
internal class SharedCopyStep(
    private val sharedCopySync: SharedCopySync,
    private val registry: SharedLinksRegistry,
    private val mirror: SharedLinksMirror,
    private val journal: SharedLinksJournal,
    private val logger: Logger,
) : SyncRunStep {

    override val id = LibrarySyncSteps.SHARED_COPIES

    override val documentNames = setOf(SharedLinksFormat.NAME)

    /** A connection made before sharing was asked for fails here too, and never fails the run. */
    override suspend fun run(
        folder: SyncFolder,
        index: Map<SyncKey, SyncIndexEntry>,
        documentRevisions: Map<String, String>,
        sharedFiles: List<RemoteSharedFile>,
    ) = sharedCopySync.synchronize(
        folder = folder,
        index = index,
        sharedFiles = sharedFiles,
        registryRevision = documentRevisions[SharedLinksFormat.NAME],
    )

    /** So that screens follow a share made or stopped elsewhere. */
    override suspend fun onDocumentChanged(folder: SyncFolder) {
        val read = registry.read(folder)
        logger.log(
            "Sync feed: shared links read (" + when (read) {
                is SharedLinksRegistry.Read.Readable -> "${read.document.links.size} links"
                is SharedLinksRegistry.Read.FromNewerVersion -> "of a newer version"
            } + ")",
        )
    }

    /**
     * The shares themselves stay in the folder: connecting the same account again brings them back, and the journal's
     * renames not yet applied are lost with it, which can leave those shares orphaned until stopped, and so are its
     * stops, which leaves deleted items' links working (the Disconnect confirmation warns:
     * `SharedLinksRepository.hasPendingStops`). Each is cleared even where the other could not be.
     */
    override suspend fun forget() {
        logger.recovering(describe = { "Could not clear the shared links mirror: ${it::class.simpleName}" }, fallback = {}) { mirror.clear() }
        logger.recovering(describe = { "Could not clear the shared links journal: ${it::class.simpleName}" }, fallback = {}) { journal.clear() }
    }
}
