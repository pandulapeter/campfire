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
 * What sync remembers between runs on this device besides the tokens - the index, and one note about the credentials -
 * next to the preferences and so outside `library/`: neither is the user's data, and an export must not carry them.
 *
 * The index is an opaque string here. What is in it belongs to the repository that writes it, and the storage layer
 * has no business knowing its shape. The note is about credentials but goes with the index: its only client is the
 * repository, which decides when a previous installation's credentials are forgotten.
 */
interface SyncIndexLocalSource {

    /**
     * What the last successful run saw, which is how the next one tells a change from a deletion. Null only when there is
     * none; one that is there and cannot be read throws [LibraryStorageException], since a run that took it for none
     * would bring back every file deleted since and upload again every file deleted elsewhere.
     */
    suspend fun loadSyncIndex(): String?

    suspend fun saveSyncIndex(document: String?)

    /**
     * Whether forgetting the stored credentials is still owed: noted before a fresh installation forgets what a
     * previous one left in a store that outlived it, and cleared once that has succeeded or a connection made here has
     * replaced it. A file of this installation's own, so that the start up after a failed attempt - which is no longer
     * a first launch - still knows the credentials in the store are not this installation's. Kept out of the device
     * backup: it is about this device's Keychain, and another device has its own.
     */
    suspend fun isForgettingCredentialsOwed(): Boolean

    suspend fun setForgettingCredentialsOwed(isOwed: Boolean)
}
