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
 * What this device keeps about the connected account's shared links, next to the sync index and under its rules: the
 * mirror (the account's list of shares as last read) and the journal (renames and deletions of shared items made here
 * that the list has not been told yet). Both describe one account's folder, so neither goes into a device backup, an
 * export, or a sync.
 *
 * Both are opaque strings here, as the index is: their shape belongs to sync. Null only for a document that is not
 * there; one that is there and cannot be read throws [LibraryStorageException], since a journal taken for none would be
 * written over without the renames it still holds. Saving null deletes the document.
 */
interface SharedLinksLocalSource {

    suspend fun loadSharedLinksMirror(): String?

    suspend fun saveSharedLinksMirror(document: String?)

    suspend fun loadSharedLinksJournal(): String?

    suspend fun saveSharedLinksJournal(document: String?)
}
