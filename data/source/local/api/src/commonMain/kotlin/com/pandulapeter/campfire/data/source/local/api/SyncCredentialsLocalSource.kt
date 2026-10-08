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
 * Where sync keeps the tokens of the connected account, next to the preferences and so outside `library/`: they are
 * not the user's data, and an export must not carry them.
 *
 * An opaque string here. What is in it belongs to the remote source that writes it, and the storage layer has no
 * business knowing its shape.
 */
interface SyncCredentialsLocalSource {

    /**
     * The tokens of the connected account. Null when nothing is connected, and also when what was stored can no
     * longer be read, which only connecting again can answer. Credentials that are there and cannot be read right now
     * - a secret store that refuses for a moment - throw [LibraryStorageException] instead, since taking them for none
     * would have the next authorization written over tokens that still work.
     *
     * Android keeps them encrypted with a key held by the Keystore and iOS in the Keychain. Desktop and the web keep
     * them in a file of app-private storage, which is as private as the platform makes it - the origin on the web,
     * the user's own data directory on desktop - and so readable by anything that can already read the user's files.
     */
    suspend fun loadSyncCredentials(): String?

    suspend fun saveSyncCredentials(document: String?)
}
