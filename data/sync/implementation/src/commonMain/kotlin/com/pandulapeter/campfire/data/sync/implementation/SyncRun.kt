/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.data.model.domain.SyncProgress
import com.pandulapeter.campfire.data.source.remote.api.SyncFolder
import kotlinx.serialization.json.JsonObject

/** What stays the same for the whole of one [SyncEngine.synchronize] call, handed to everything a run does. */
internal class SyncRun(
    val provider: SyncFolder,
    val accountId: String,
    val lastSyncedAt: Long,
    val onProgress: (SyncProgress) -> Unit,
    val onIndexChanged: suspend (snapshot: () -> SyncIndexDocument) -> Unit,
    val onLocalFileChanged: suspend (SyncKey) -> Unit,
)

/**
 * What stays the same for one pass of a run: the [index] the pass was planned from, the cloud folder's files as it
 * listed them, and what [preparePass] worked out.
 */
internal class SyncPass(
    val run: SyncRun,
    val index: Map<SyncKey, SyncIndexEntry>,
    val remoteFiles: Map<SyncKey, RemoteFileState>,
    val caseCollisions: Set<SyncKey>,
    val syncedPreferences: JsonObject?,
)
