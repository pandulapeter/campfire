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
)

/**
 * Whether [changes] hold a library file that is not as the run left it, or a document other than the preferences, which
 * nothing here can judge: a revision the index does not hold is another device's write, and a deletion of a file the
 * index still holds is another device's deletion. Revisions are only ever compared for equality, as the planner does.
 */
internal fun SyncWatchPoint.hasOtherChangesThanPreferences(changes: RemoteChanges) =
    changes.files.any { index[SyncKey(kind = it.kind, name = it.name)]?.remoteRevision != it.revision } ||
        changes.deletedFiles.any { SyncKey(kind = it.kind, name = it.name) in index } ||
        changes.documents.any { it != SyncedPreferencesDocument.FILE_NAME }

/** Whether the folder's `preferences.json`, [bytes] or null where there is none, is not the one the run left there. */
internal fun SyncWatchPoint.isOtherPreferences(bytes: ByteArray?) =
    bytes?.let(SyncedPreferencesDocument::decode) != syncedPreferences
