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

/**
 * The content of a remote file and the revision it is at: a library file as `SyncProvider.download` fetched it, or a
 * document of the folder's own, such as `preferences.json`.
 *
 * @param revision The same opaque revision a [RemoteFile] carries, handed back to `SyncProvider.uploadDocument` so that
 *   a write made elsewhere since this was read is reported as a conflict rather than overwritten.
 */
class RemoteDocument(
    val bytes: ByteArray,
    val revision: String,
)
