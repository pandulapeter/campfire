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
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteChanges
import com.pandulapeter.campfire.data.sync.implementation.SyncIndexEntry
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import com.pandulapeter.campfire.data.sync.implementation.preferences.SyncedPreferencesDocument
import com.pandulapeter.campfire.data.sync.implementation.run.folded
import kotlinx.serialization.json.JsonObject

/**
 * Where [SyncRemoteWatcher] watches the folder from after a run that completed: the folder it ran against, the cursor of
 * its last listing, and what it left the two sides at. Every change after that cursor is either the run's own, and so
 * matches [index] or [syncedPreferences], or another device's.
 */
internal data class SyncWatchPoint(
    val folder: SyncFolder,
    val cursor: String,
    val index: Map<SyncKey, SyncIndexEntry>,
    /** `preferences.json` as the run left it in the folder. */
    val syncedPreferences: JsonObject?,
) {

    /**
     * The index by folded name, see [folded]: the run files an entry under this device's spelling, while the feed names
     * the folder's, and a service that ignores case and Unicode form takes the two for one file.
     */
    val indexByFolded: Map<SyncKey, List<SyncIndexEntry>> by lazy { index.entries.groupBy({ it.key.folded() }, { it.value }) }
}

/**
 * Whether [changes] hold a library file that is not as the run left it, or a document other than the preferences, which
 * nothing here can judge: a revision the index does not hold is another device's write, and a deletion of a file the
 * index still holds is another device's deletion. Revisions are only ever compared for equality, as the planner does.
 * Names are matched as the run files them, ignoring case and Unicode form where the index has no entry under the exact
 * one, so a service that holds two spellings apart keeps its exact matches.
 */
internal fun SyncWatchPoint.hasOtherChangesThanPreferences(changes: RemoteChanges) =
    changes.files.any { !isOwnRevision(SyncKey(kind = it.kind, name = it.name), it.revision) } ||
        changes.deletedFiles.any { isIndexed(SyncKey(kind = it.kind, name = it.name)) } ||
        changes.documents.any { it != SyncedPreferencesDocument.FILE_NAME }

private fun SyncWatchPoint.isOwnRevision(key: SyncKey, revision: String) = index[key]?.let { it.remoteRevision == revision }
    ?: indexByFolded[key.folded()].orEmpty().any { it.remoteRevision == revision }

private fun SyncWatchPoint.isIndexed(key: SyncKey) = key in index || key.folded() in indexByFolded

/** Whether the folder's `preferences.json`, [bytes] or null where there is none, is not the one the run left there. */
internal fun SyncWatchPoint.isOtherPreferences(bytes: ByteArray?) =
    bytes?.let(SyncedPreferencesDocument::decode) != syncedPreferences
