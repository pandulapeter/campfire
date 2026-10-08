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

import com.pandulapeter.campfire.data.model.domain.SyncFailureReason

/**
 * One cloud service: its account ([SyncConnection]) and its folder ([SyncFolder]). Everything a provider does
 * differently lives behind this: Dropbox works in paths, Drive in file ids, WebDAV in URLs, and the sync engine above
 * never learns which - it is handed the folder half alone.
 */
interface SyncProvider : SyncConnection, SyncFolder

/**
 * A failure that makes every further call of a run pointless, and so ends it, where any other one is one file's
 * problem. Sealed, so that a new kind is one subclass here rather than one more case in every list of what ends a run.
 *
 * @param reason What the user is told the run ended in.
 */
sealed class SyncRunEndingException(
    message: String,
    cause: Throwable?,
    val reason: SyncFailureReason,
) : Exception(message, cause)

/** The credentials are gone, were refused or were revoked: only connecting again can fix it. */
class SyncAuthorizationException(message: String, cause: Throwable? = null) :
    SyncRunEndingException(message, cause, SyncFailureReason.AUTHORIZATION)

/** The service could not be reached. Trying again later is a reasonable thing to do. */
class SyncNetworkException(message: String, cause: Throwable? = null) :
    SyncRunEndingException(message, cause, SyncFailureReason.NETWORK)

/**
 * The service has no room left for what is being uploaded. Told apart from the refusal of one file because every
 * upload after it would be answered the same way, each only after sending its file.
 */
class SyncRemoteStorageFullException(message: String, cause: Throwable? = null) :
    SyncRunEndingException(message, cause, SyncFailureReason.REMOTE_STORAGE_FULL)
