/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.run

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.library.SyncCollection
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.library.SyncCollections
import com.pandulapeter.campfire.data.model.recovering
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncFolder
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteFile
import com.pandulapeter.campfire.data.sync.implementation.SyncIndexEntry
import com.pandulapeter.campfire.data.sync.implementation.SyncKey

/**
 * Brings back from the folder's history ([SyncFolder.restoreDeleted]) the content files a document here names that no
 * side holds and the index does not know. A device deletes a file from the folder once none of its documents names it,
 * and a version that knows nothing of such files may then put back an edit of a document that named it: the document
 * returns everywhere, naming a file nothing holds. The restored copy is downloaded by the next run, the one the run
 * asks to follow it, and checked against its name like any download.
 *
 * Each name is asked for once per process ([asked]): most of them are files a document named before the file ever
 * reached the library (in Campfire, a picture placeholder offering to add one), which have no history to restore, and a
 * restored copy found damaged is not asked for again either. A name whose request failed is asked again by the next run.
 *
 * A reference names no collection ([SyncReferences]), so a name is asked of each content collection in turn until one
 * brings it back.
 */
internal class LostFileRestorer(
    private val libraryFileLocalSource: LibraryFileLocalSource,
    collections: SyncCollections,
    private val references: Map<SyncCollection, SyncReferences>,
    private val logger: Logger,
) {

    private val asked = mutableSetOf<String>()
    private val contentCollections = collections.all.filter { it.role == SyncCollection.Role.CONTENT_FILE }

    /**
     * Restores what the referencing documents here name, as the run's last pass listed them ([local]) and as this run
     * wrote them since (those whose index entry it changed, read now), that neither listing nor the index ([after], the
     * run's last) holds: at most [MAXIMUM_RESTORES], one at a time. Answers whether any came back.
     */
    suspend fun restoreMissing(
        provider: SyncFolder,
        local: LocalListing,
        listed: List<RemoteFile>,
        before: Map<SyncKey, SyncIndexEntry>,
        after: Map<SyncKey, SyncIndexEntry>,
    ): Boolean {
        val held = (listed.map { SyncKey(kind = it.kind, name = it.name) } + local.states.map { it.key } + local.tooLarge + local.unreadable + after.keys)
            .filter { it.kind.role == SyncCollection.Role.CONTENT_FILE }
            .mapTo(mutableSetOf()) { LibraryFiles.identityKey(it.name) }
        // A document this run deleted here names nothing any more; one it wrote is read again for what it names now.
        val listedSongs = local.imageReferences?.filterKeys { it in after || it !in before }?.values?.flatten().orEmpty()
        val written = after.filter { (key, entry) -> key.kind in references && before[key]?.localHash != entry.localHash }.keys
        val named = listedSongs + written.flatMap { namedBy(it) }
        val missing = named.distinct().filter { LibraryFiles.identityKey(it) !in held && it !in asked }.take(MAXIMUM_RESTORES)
        var isAnyRestored = false
        for (name in missing) {
            asked += name
            val isRestored = logger.recovering(describe = { "Could not restore \"$name\": ${it.message}" }, fallback = { null }) {
                contentCollections.any { collection -> provider.restoreDeleted(collection, name) }
            }
            if (isRestored == null) {
                asked -= name
                break
            }
            if (isRestored) logger.log("Sync: restored \"$name\", which a document names and nothing held, from the folder's history")
            isAnyRestored = isAnyRestored || isRestored
        }
        return isAnyRestored
    }

    private suspend fun namedBy(document: SyncKey) = logger.recovering(
        describe = { "Could not read \"${document.path}\": ${it.message}" },
        fallback = { emptySet() },
    ) {
        val namedBy = references.getValue(document.kind)
        libraryFileLocalSource.readLibraryFile(document.kind, document.name)?.let(namedBy::of).orEmpty()
    }

    private companion object {
        /** A run asks for no more than this, so a library whose documents name many files nobody holds does not stall it. */
        const val MAXIMUM_RESTORES = 20
    }
}
