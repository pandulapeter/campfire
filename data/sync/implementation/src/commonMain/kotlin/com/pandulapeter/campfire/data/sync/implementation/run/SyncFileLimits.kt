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

import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteDocument
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * The largest file a run reads or uploads here, or downloads, under this key: what the app itself takes under such a
 * name, since a larger one was put there from outside and no other device would take it either. A song or a setlist is
 * held to [ImportLimits.MAX_TEXT_FILE_SIZE], a library file to [ImportLimits.MAX_ATTACHMENT_FILE_SIZE]: every picture's
 * name is an attachment's too (`LibraryFiles.ATTACHMENT_EXTENSIONS`), since an attached photo is kept at full size, so
 * no name tells a picture held to its 8 MB from an attachment.
 */
internal val SyncKey.maximumSize get() = if (kind == LibraryFileKind.FILE) ImportLimits.MAX_ATTACHMENT_FILE_SIZE else ImportLimits.MAX_TEXT_FILE_SIZE

/**
 * Whether a file of [size] bytes is one a run moves alone: every file is held in memory whole while it moves, several at
 * once (`SyncEngine.CONCURRENT_TRANSFERS`), and a few attachments of 50 MB each are more than a phone's heap. Below it, at
 * most the size a song or a picture may be, that many at once are what a phone has always spared a run.
 */
internal fun isLargeFile(size: Long) = size > ImportLimits.MAX_TEXT_FILE_SIZE

/**
 * A download is held in memory whole, so a file over its limit ([maximumSize]) is refused. Refused here rather than
 * left out of the listing: a file the planner cannot see on the remote is a file it takes for deleted there, and a
 * large one that is already in the library would be deleted locally for it. Thrown, it is one file's failure like
 * any other - logged, the index left alone, tried again by the next run. The provider holds the download to the
 * same limit, since the listing's size is not the file's once another device writes it again.
 */
internal suspend fun SyncPass.downloadWithinLimit(key: SyncKey): RemoteDocument {
    val size = remoteFiles[key]?.size ?: 0
    val limit = key.maximumSize
    if (size > limit) error("The remote file is $size bytes, which is more than the $limit a run downloads.")
    return run.provider.download(key.kind, key.name, maxSize = limit)
}

/** [block] holding this permit, or without one where there is none to take. */
internal suspend fun <T> Semaphore?.withOptionalPermit(block: suspend () -> T): T =
    if (this == null) block() else withPermit { block() }
