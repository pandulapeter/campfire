/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api.model

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind

/** One file [com.pandulapeter.campfire.data.source.remote.api.SyncProvider.delete] is asked to remove. */
data class RemoteDeletion(
    val kind: LibraryFileKind,
    val name: String,
    /** The revision the caller last saw, or null to delete whatever is there. */
    val expectedRevision: String?,
)
