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

import com.pandulapeter.campfire.data.source.remote.api.sync.SyncDocumentTooLargeException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncFolder
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteDocument

/**
 * [SyncFolder.downloadDocument], except that a document too large to download is answered as an empty one at its
 * revision. Every reader of a folder document takes no bytes as unreadable, so the preferences step writes this device's
 * values over it and the registry counts as empty until a sharing action writes over it, both against that revision,
 * which is what lets the write through. The user put it there; recovering beats keeping it.
 */
internal suspend fun SyncFolder.downloadFolderDocument(name: String): RemoteDocument? = try {
    downloadDocument(name)
} catch (exception: SyncDocumentTooLargeException) {
    RemoteDocument(bytes = ByteArray(0), revision = exception.revision)
}
