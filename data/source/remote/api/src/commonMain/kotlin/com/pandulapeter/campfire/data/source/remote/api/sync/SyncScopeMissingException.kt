/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api.sync

/**
 * The service accepts the credentials, but not for this call: the user connected before Campfire asked for the
 * permission it needs, and a service grants a connection only what was asked for when it was made. Only connecting
 * again adds it, while everything the connection could already do keeps working.
 *
 * Deliberately neither a [SyncRunEndingException] nor a [SyncAuthorizationException]: the files still sync, so a run
 * that meets it skips only what needed the permission, and the connection is left as it is. A provider throws it
 * without trying to renew the token first, since a renewed token carries the same permissions.
 *
 * @param requiredScope The permission the service named as missing, where it named one, for the log.
 */
class SyncScopeMissingException(
    message: String,
    val requiredScope: String?,
) : Exception(message)
