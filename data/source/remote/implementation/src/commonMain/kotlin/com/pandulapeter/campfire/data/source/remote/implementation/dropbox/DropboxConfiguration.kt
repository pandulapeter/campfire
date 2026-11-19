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

import com.pandulapeter.campfire.data.model.library.SyncCollections

/**
 * What the app registered with Dropbox and keeps in its app folder, which [DropboxSyncProvider] is built with.
 *
 * @param appKey The registered app's key, a public client identifier (PKCE, no client secret), named in the
 *   authorization URL and every token request.
 * @param collections The library's collections, a folder of the app folder each, named by its id.
 * @param maxDocumentSize The largest folder document downloaded, refused above it with its revision.
 */
internal data class DropboxConfiguration(
    val appKey: String,
    val collections: SyncCollections,
    val maxDocumentSize: Long,
)
