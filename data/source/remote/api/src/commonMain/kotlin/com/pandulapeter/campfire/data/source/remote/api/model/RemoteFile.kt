package com.pandulapeter.campfire.data.source.remote.api.model

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind

/**
 * One file in the remote library folder.
 *
 * @param revision Whatever the provider uses to say "this exact version": a Dropbox `rev`, a Drive `headRevisionId`,
 *   an ETag. It is deliberately opaque - nothing outside the provider may parse it, compare it for ordering or
 *   assume a format - which is what lets a second provider be added without touching the sync engine.
 * @param contentHash The provider's own hash of the content, in the provider's own format, or null where it does not
 *   offer one. Only ever compared against a hash from the *same* provider, and only to skip a transfer, never to
 *   decide what changed.
 */
data class RemoteFile(
    val kind: LibraryFileKind,
    val name: String,
    val revision: String,
    val contentHash: String?,
    val size: Long
)
