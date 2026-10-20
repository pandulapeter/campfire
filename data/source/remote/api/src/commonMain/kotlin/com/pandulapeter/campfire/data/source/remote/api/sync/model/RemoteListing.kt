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

/**
 * Everything in the remote library folder.
 *
 * Sync lists the whole folder every run, which for a library of songs is a single request and is right even after a run
 * that was interrupted half way. [cursor] is only for watching the folder afterwards, see
 * [com.pandulapeter.campfire.data.source.remote.api.sync.SyncFolder.awaitChanges].
 *
 * @param cursor Where the service's feed of changes stood when the listing was taken, opaque like a revision, or null
 *   for a provider that offers no such feed.
 */
data class RemoteListing(
    val files: List<RemoteFile>,
    val cursor: String? = null,
)
