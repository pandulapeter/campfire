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

import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteDocument
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * A download is held in memory whole, so a file over its collection's limit (`SyncCollection.maxSize`) is refused.
 * Refused here rather than left out of the listing: a file the planner cannot see on the remote is a file it takes for
 * deleted there, and a large one that is already in the library would be deleted locally for it. Thrown, it is one file's failure like
 * any other - logged, the index left alone, tried again by the next run. The provider holds the download to the
 * same limit, since the listing's size is not the file's once another device writes it again.
 */
internal suspend fun SyncPass.downloadWithinLimit(key: SyncKey): RemoteDocument {
    val size = remoteFiles[key]?.size ?: 0
    val limit = key.kind.maxSize
    if (size > limit) error("The remote file is $size bytes, which is more than the $limit a run downloads.")
    return run.provider.download(key.kind, key.name, maxSize = limit)
}

/** [block] holding this permit, or without one where there is none to take. */
internal suspend fun <T> Semaphore?.withOptionalPermit(block: suspend () -> T): T =
    if (this == null) block() else withPermit { block() }
