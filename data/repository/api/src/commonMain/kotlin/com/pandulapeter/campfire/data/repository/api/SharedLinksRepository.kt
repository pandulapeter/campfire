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
import com.pandulapeter.campfire.data.model.share.ShareState
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
     * The shares as last read, with the renames and stops this device made since applied as they will be once the list
     * is told, most recent first; empty while no account is connected, and before the first read for one that is. Never
     * asks the service: [refresh] does.
     */
    val shares: Flow<List<Share>>

    /** Reads the account's list again into [shares]. Null when it worked. */
    suspend fun refresh(): SharingFailure?

    /**
     * Uploads [copy] as the shared copy of the library file [fileName], makes a public link to it, and adds it to the
     * account's list. The caller has checked [shares], just refreshed, for a share of the same item; one that another
     * device adds in the meantime is answered instead, and the copy uploaded here deleted, so an item never has two
     * links. The renames and stops this device has not told the list yet go first, and a share fails while they cannot.
     * A share of the item whose copy is gone ([ShareState.COPY_MISSING]) is replaced by this one, never answered.
     * Whatever fails after the upload deletes the copy again, as far as it can, except a copy the account's list may
     * name: a failure that leaves unclear whether the list was written keeps it.
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
     * or an entry already gone counts as stopped. A list that cannot be edited once the copy is gone leaves the entry
     * to the next sync run, and the share is stopped (and no longer in [shares]) meanwhile. Null when it worked; a
     * failure means the link still works, unless not even the entry's removal could be left to that run. May wait for
     * a sync run's shared-copy step.
     */
    suspend fun stopSharing(shareId: String): SharingFailure?

    /**
     * Tells the shares that the library file [fileName] of [kind] is now [newFileName], so that a share of it follows
     * and its link keeps working. Only this device can tell a rename from a deletion followed by a new file, so it is
     * noted here before the service is asked: the next sync run carries whatever the service has not been told yet.
     * While an account is connected it is noted whether or not [shares] names the item, since another device may have
     * shared it since the last read, and resolved against the account's list when applied: a rename of an item nobody
     * shares changes nothing there. Otherwise nothing is noted for an item [shares] does not name. Never throws but
     * cancellation, and never waits for the service.
     */
    suspend fun followRename(kind: SharedLinkKind, fileName: String, newFileName: String)

    /**
     * Stops sharing the library file [fileName] of [kind], which was deleted in Campfire, the way [followRename] notes
     * a rename: whatever share of it the account's list names when applied. Never throws but cancellation, and never
     * waits for the service.
     */
    suspend fun stopSharingDeleted(kind: SharedLinkKind, fileName: String)

    /**
     * Stops sharing everything, for a library deleted as a whole, the way [stopSharingDeleted] does: while an account
     * is connected, every share the account's list names when applied, else everything [shares] names.
     */
    suspend fun stopSharingEverything()

    /**
     * Stops every share of the account now, as Settings' Stop sharing everything asks, rather than noting it the way
     * [stopSharingEverything] does: deletes each shared copy in its folder, those no list names included (left
     * behind by a share that failed halfway), then removes the entries whose copies went. An entry is never removed
     * while its copy may still be served, so a failure halfway leaves only shares that still work listed; entries the
     * list could not lose are left to the next sync run, as [stopSharing] leaves them. Null when it worked. May wait
     * for a sync run's shared-copy step.
     */
    suspend fun stopSharingEverythingNow(): SharingFailure?
}
