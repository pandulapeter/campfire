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
 * One shared copy in the remote folder, see [com.pandulapeter.campfire.data.source.remote.api.sync.SyncFolder.uploadSharedFile].
 * The revision and the content hash follow the rules of a [RemoteFile]: opaque, and compared only with the same
 * provider's.
 *
 * @param shareId The id the copy was uploaded under, which names it for as long as it is shared.
 */
data class RemoteSharedFile(
    val shareId: String,
    val revision: String,
    val contentHash: String?,
    val size: Long,
)
