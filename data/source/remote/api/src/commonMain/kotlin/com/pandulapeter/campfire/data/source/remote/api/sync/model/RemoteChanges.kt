/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api.sync.model

import com.pandulapeter.campfire.data.model.library.LibraryFileKind

/**
 * What changed in the remote folder since a cursor, see [com.pandulapeter.campfire.data.source.remote.api.sync.SyncFolder.changesSince].
 * The changes of this device's own runs are in it too: telling them apart is the caller's business.
 *
 * @param files The library files written since, as they are now.
 * @param deletedFiles The library files deleted since.
 * @param documents The folder's own documents written or deleted since, by name.
 * @param cursor Where the feed stands after these changes, for the next call.
 */
data class RemoteChanges(
    val files: List<RemoteFile>,
    val deletedFiles: List<DeletedFile>,
    val documents: Set<String>,
    val cursor: String,
) {

    data class DeletedFile(
        val kind: LibraryFileKind,
        val name: String,
    )
}
