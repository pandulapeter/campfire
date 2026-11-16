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

import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.sync.implementation.SyncKey

/**
 * The hash the planner and the index know a local file by: its content's, except for a picture, whose name already is
 * its content's hash, see [readLocalListing]. Every comparison with the index's `localHash` goes through this, or a
 * picture would read as changed on every run.
 */
internal fun localHashOf(key: SyncKey, bytes: ByteArray) = if (key.kind == LibraryFileKind.FILE) localImageHash(bytes.size.toLong()) else localContentHash(bytes)

internal fun localImageHash(size: Long) = "$IMAGE_HASH_PREFIX$size"

private const val IMAGE_HASH_PREFIX = "size:"
