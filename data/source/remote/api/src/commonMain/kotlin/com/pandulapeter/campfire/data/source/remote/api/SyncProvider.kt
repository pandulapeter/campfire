/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api

/**
 * One cloud service: its account ([SyncConnection]) and its folder ([SyncFolder]). Everything a provider does
 * differently lives behind this: Dropbox works in paths, Drive in file ids, WebDAV in URLs, and the sync engine above
 * never learns which - it is handed the folder half alone.
 */
interface SyncProvider : SyncConnection, SyncFolder

/** The credentials are gone, were refused or were revoked: only connecting again can fix it. */
class SyncAuthorizationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The service could not be reached. Trying again later is a reasonable thing to do. */
class SyncNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The service has no room left for what is being uploaded. Told apart from the refusal of one file because every
 * upload after it would be answered the same way, each only after sending its file.
 */
class SyncRemoteStorageFullException(message: String, cause: Throwable? = null) : Exception(message, cause)
