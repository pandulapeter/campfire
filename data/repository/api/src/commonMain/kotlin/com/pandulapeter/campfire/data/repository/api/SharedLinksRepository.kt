/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.share.CreateShareResult
import com.pandulapeter.campfire.data.model.share.Share
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.share.SharingFailure
import kotlinx.coroutines.flow.Flow

/**
 * The songs and setlists shared as links from the connected sync account. The account's own list of them, in its
 * cloud folder, is the truth; this device keeps the last copy it read, so that the screens know what is shared without
 * a request, and only the actions below ask the service.
 *
 * None of it waits for a sync run, or holds one up: a share made right after connecting must not wait for the whole
 * first sync. Every change to the list is checked against the revision it was read at, so a run and an action, or
 * two devices, never undo each other's change.
 */
interface SharedLinksRepository {

    /**
     * The shares as last read, most recent first; empty while no account is connected, and before the first read for
     * one that is. Never asks the service: [refresh] does.
     */
    val shares: Flow<List<Share>>

    /** Reads the account's list again into [shares]. Null when it worked. */
    suspend fun refresh(): SharingFailure?

    /**
     * Uploads [copy] as the shared copy of the library file [fileName], makes a public link to it, and adds it to the
     * account's list. The caller has checked [shares], just refreshed, for a share of the same item; one that another
     * device adds in the meantime is answered instead, and the copy uploaded here deleted, so an item never has two
     * links. Whatever fails after the upload deletes the copy again, as far as it can.
     *
     * Never throws but cancellation. Answers [CreateShareResult.Shared] or [CreateShareResult.Failed] only.
     */
    suspend fun share(
        kind: SharedLinkKind,
        fileName: String,
        title: String,
        copy: ByteArray,
    ): CreateShareResult

    /**
     * Deletes the shared copy [shareId], which ends its link for good, then removes it from the account's list. A copy
     * or an entry already gone counts as stopped. Null when it worked.
     */
    suspend fun stopSharing(shareId: String): SharingFailure?

    /**
     * Tells the shares that the library file [fileName] of [kind] is now [newFileName], so that a share of it follows
     * and its link keeps working. Only this device can tell a rename from a deletion followed by a new file, so it is
     * noted here before the service is asked: the next sync run carries whatever the service has not been told yet.
     * Nothing is noted for an item [shares] does not name. Never throws but cancellation, and never waits for the
     * service.
     */
    suspend fun followRename(kind: SharedLinkKind, fileName: String, newFileName: String)

    /**
     * Stops sharing the library file [fileName] of [kind], which was deleted in Campfire, the way [followRename] notes
     * a rename. Never throws but cancellation, and never waits for the service.
     */
    suspend fun stopSharingDeleted(kind: SharedLinkKind, fileName: String)

    /** Stops sharing everything [shares] names, for a library deleted as a whole, the way [stopSharingDeleted] does. */
    suspend fun stopSharingEverything()

    /**
     * Stops every share of the account now, as Settings' Stop sharing everything asks, rather than noting it the way
     * [stopSharingEverything] does: deletes each shared copy in its folder, those no list names included (left
     * behind by a share that failed halfway), then removes the entries whose copies went. An entry is never removed
     * while its copy may still be served, so a failure halfway leaves only shares that still work listed. Null when
     * it worked.
     */
    suspend fun stopSharingEverythingNow(): SharingFailure?
}
