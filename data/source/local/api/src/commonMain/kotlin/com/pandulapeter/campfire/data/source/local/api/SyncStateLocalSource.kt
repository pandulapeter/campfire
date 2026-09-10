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
     * The tokens of the connected account. Null when nothing is connected.
     *
     * This is app-private storage, which is as private as the platform makes it: on Android and iOS the sandbox,
     * on the web the origin, on desktop the user's own data directory. A refresh token in it is readable by anything
     * that can already read the user's files, so a platform secret store (Keychain, Keystore) is the next step.
     */
    suspend fun loadSyncCredentials(): String?

    suspend fun saveSyncCredentials(document: String?)

    /** What the last successful run saw, which is how the next one tells a change from a deletion. */
    suspend fun loadSyncIndex(): String?

    suspend fun saveSyncIndex(document: String?)
}
