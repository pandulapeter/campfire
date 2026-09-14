/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.api

/**
 * Where sync keeps the two documents it has to remember between runs, next to the preferences and so outside
 * `library/`: neither of them is the user's data, and an export must not carry them.
 *
 * Both are opaque strings here. What is in them belongs to the layers that write them - the credentials to the
 * remote source, the index to the repository - and the storage layer has no business knowing either shape.
 */
interface SyncStateLocalSource {

    /**
     * The tokens of the connected account. Null when nothing is connected, and also when what was stored can no
     * longer be read, which only connecting again can answer.
     *
     * Android keeps them encrypted with a key held by the Keystore and iOS in the Keychain. Desktop and the web keep
     * them in a file of app-private storage, which is as private as the platform makes it - the origin on the web,
     * the user's own data directory on desktop - and so readable by anything that can already read the user's files.
     */
    suspend fun loadSyncCredentials(): String?

    suspend fun saveSyncCredentials(document: String?)

    /** What the last successful run saw, which is how the next one tells a change from a deletion. */
    suspend fun loadSyncIndex(): String?

    suspend fun saveSyncIndex(document: String?)
}
