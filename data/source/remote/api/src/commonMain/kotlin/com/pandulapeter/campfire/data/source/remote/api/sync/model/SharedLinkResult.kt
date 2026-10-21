/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api.sync.model

/**
 * What asking for a public link to a shared copy ended in, see
 * [com.pandulapeter.campfire.data.source.remote.api.sync.SyncFolder.createSharedLink].
 */
sealed interface SharedLinkResult {

    /**
     * The copy has a link anyone can open. Its two parts are all a download needs, and they are what a Campfire link
     * carries: the download address is built by the app from them, never taken from a link.
     *
     * @param fileId The service's id of the shared file, the same for every link the file ever has.
     * @param rlkey The key of this one link: a link removed and made again gets a new one, and the old address stays
     *   dead.
     */
    data class Created(
        val fileId: String,
        val rlkey: String,
    ) : SharedLinkResult

    /** A link exists, but the account lets only people signed in to it, or to its team, open it. */
    data object NotPublic : SharedLinkResult

    /** The service made no link the app can use: the account may not share files, or the link is of another form. */
    data object Refused : SharedLinkResult
}
