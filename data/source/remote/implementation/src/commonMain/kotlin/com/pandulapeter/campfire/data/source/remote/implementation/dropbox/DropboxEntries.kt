/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.dropbox

import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteFile

internal fun List<DropboxEntry>.toRemoteFiles() = mapNotNull { entry ->
    if (entry.tag != "file") return@mapNotNull null
    val kind = entry.libraryKind() ?: return@mapNotNull null
    RemoteFile(
        kind = kind,
        name = entry.name,
        revision = entry.rev,
        contentHash = entry.contentHash,
        size = entry.size,
    )
}

/** Only the two library folders, one level deep: anything the user put deeper into the app folder is theirs. */
internal fun DropboxEntry.libraryKind() = LibraryFileKind.entries
    .firstOrNull { pathLower.startsWith("/${it.id}/") }
    ?.takeIf { depth == 2 }

/** How many folders deep the entry is: 1 for one at the top of the app folder. */
internal val DropboxEntry.depth get() = pathLower.count { it == '/' }
