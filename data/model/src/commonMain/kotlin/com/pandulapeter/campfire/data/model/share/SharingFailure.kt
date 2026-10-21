/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.share

/** Why sharing something, stopping a share, or reading the list of shares did not happen. */
enum class SharingFailure {

    /** No account is connected. */
    NOT_CONNECTED,

    /** The connection was made before Campfire asked for permission to make links: connecting again fixes it. */
    SCOPE_MISSING,

    /** The account only makes links that its own people can open, so the copy was deleted again. */
    NOT_PUBLIC,

    /** The service made no link the app can use, so the copy was deleted again. */
    REFUSED,

    /** The cloud folder is full. */
    REMOTE_STORAGE_FULL,

    /** The service could not be reached. Trying again may work. */
    NETWORK,

    /** The service no longer accepts the connection. */
    AUTHORIZATION,

    /** The list of shares was written by a newer version of Campfire, and this one leaves it alone. */
    NEWER_VERSION,

    UNKNOWN,
}
