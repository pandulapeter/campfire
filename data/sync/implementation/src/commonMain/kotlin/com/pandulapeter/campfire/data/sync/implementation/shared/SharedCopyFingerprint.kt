/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.shared

import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash

/**
 * What a shared copy is built from, as one hash (`SharedCopyBuild.inputs`): the item's kind, name, and content hash,
 * then, for a setlist, every song it names in its order with its content hash, or `-` for one not in the library.
 *
 * Every hash is the sync index's `localHash` of the file, so a run can tell from its index alone, without reading a song,
 * whether the copy could differ from the one last built. The names are the ones the registry and the setlist document
 * spell, never this device's spelling of the file, so that every device makes the same fingerprint of the same files.
 *
 * @param songs The setlist's songs with their hashes, null for a song shared alone.
 */
internal fun sharedCopyFingerprint(
    kind: SharedLinkKind,
    fileName: String,
    hash: String,
    songs: List<Pair<String, String?>>?,
): String {
    val lines = listOf("${kind.id} $fileName $hash") + songs.orEmpty().map { (name, songHash) -> "$name ${songHash ?: MISSING}" }
    return localContentHash(lines.joinToString(separator = "\n").encodeToByteArray())
}

private const val MISSING = "-"
