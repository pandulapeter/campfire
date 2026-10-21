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
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteSharedFile

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

/**
 * The revisions of the files at the top of the app folder, where `documentPath` puts the documents, by name. An entry
 * without a revision, which [DropboxEntry] decodes as "", names none, so it costs a download.
 */
internal fun List<DropboxEntry>.toDocumentRevisions() = filter { it.tag == "file" && it.depth == 1 && it.rev.isNotEmpty() }
    .associate { it.name to it.rev }

/** Only the two library folders, one level deep: anything the user put deeper into the app folder is theirs. */
internal fun DropboxEntry.libraryKind() = LibraryFileKind.entries
    .firstOrNull { pathLower.startsWith("/${it.id}/") }
    ?.takeIf { depth == 2 }

/** How many folders deep the entry is: 1 for one at the top of the app folder. */
internal val DropboxEntry.depth get() = pathLower.count { it == '/' }

/**
 * The shared copies, `/shared/<shareId>.zip`, one level deep like the library files. Picked out by `list` alone and never
 * by [toRemoteFiles], which the change feed shares: a run's rebuilt copy must not read as another device's change.
 */
internal fun List<DropboxEntry>.toSharedFiles() = mapNotNull { entry ->
    if (entry.tag != "file" || entry.depth != 2 || !entry.pathLower.startsWith("/$SHARED_FOLDER/")) return@mapNotNull null
    val shareId = entry.name.removeSuffix(SHARED_FILE_EXTENSION)
    if (shareId == entry.name || !shareId.isShareId()) return@mapNotNull null
    RemoteSharedFile(
        shareId = shareId,
        revision = entry.rev,
        contentHash = entry.contentHash,
        size = entry.size,
    )
}

/**
 * Where the shared copy [shareId] lives. An id that is not a plain name is refused rather than built into a path: it
 * comes from a registry anyone can edit by hand, and `../songs/x` must not reach the library.
 */
internal fun sharedFilePath(shareId: String): String {
    require(shareId.isShareId()) { "Not a share id: $shareId" }
    return "/$SHARED_FOLDER/$shareId$SHARED_FILE_EXTENSION"
}

private fun String.isShareId() = isNotEmpty() && all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }

private const val SHARED_FOLDER = "shared"
private const val SHARED_FILE_EXTENSION = ".zip"
