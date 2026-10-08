/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.data.model.domain.SyncDeletionDirection
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy

/** The one thing between a vanished library or cloud folder and a run that would carry its absence out faithfully. */
internal object DeletionGuard {

    /** The fewest deletions on one side that can stop a run, unless they are the whole library, see [isTooManyToDeleteOutOf]. */
    const val MIN_DELETIONS_TO_ASK = 5

    /**
     * The question to ask before [plan] moves, or null where it may go ahead.
     *
     * This device is asked about first: it is the side the user is looking at, and the one they can still fix. An
     * answer waives only the guard it was given for, so a run that would empty both sides asks twice, once per run -
     * two questions about one folder are better than one answer that empties both of them.
     *
     * @param planningIndexSize How many files the index the plan was made from knows.
     * @param isLocalListingEmpty Whether this device listed no library file at all.
     */
    fun check(
        plan: List<SyncOperation>,
        planningIndexSize: Int,
        isLocalListingEmpty: Boolean,
        policy: SyncDeletionPolicy,
    ): SyncEngine.Result.DeletionsNeedConfirmation? {
        val localDeletions = plan.count { it is SyncOperation.DeleteLocal }
        if (!policy.waivesLocalGuard && planningIndexSize > 0 && localDeletions.isTooManyToDeleteOutOf(planningIndexSize)) {
            return SyncEngine.Result.DeletionsNeedConfirmation(
                count = localDeletions,
                total = planningIndexSize,
                direction = SyncDeletionDirection.LOCAL,
            )
        }
        val remoteDeletions = plan.count { it is SyncOperation.DeleteRemote }
        // A library folder that is gone rather than emptied looks exactly like one emptied on purpose: the JVM
        // storage recreates it and lists nothing, and iOS lists nothing for a directory that is not there. So an
        // empty local listing with an index that is not empty always asks, however small the library - the
        // proportional rule below would let most of a small library go without a word.
        val hasLostEverythingLocally = isLocalListingEmpty && planningIndexSize > 0
        if (!policy.waivesRemoteGuard &&
            planningIndexSize > 0 &&
            remoteDeletions > 0 &&
            (hasLostEverythingLocally || remoteDeletions.isTooManyToDeleteOutOf(planningIndexSize))
        ) {
            return SyncEngine.Result.DeletionsNeedConfirmation(
                count = remoteDeletions,
                total = planningIndexSize,
                direction = SyncDeletionDirection.REMOTE,
            )
        }
        return null
    }

    /**
     * Whether a plan deletes, on one side, enough of what the last run saw that it is more likely a folder that was
     * emptied, renamed, replaced or lost than songs somebody deleted one by one - the remote folder for a deletion on
     * this device, the library folder for one in the cloud. Without this, a run would carry out such a folder's
     * absence faithfully, keeping only what had been edited since the last run - and edit-beats-deletion is exactly
     * what would make that loss quiet. More than half is the threshold, with [MIN_DELETIONS_TO_ASK] so that tidying
     * up a small library does not ask every time, except where the plan deletes everything the index knows.
     */
    internal fun Int.isTooManyToDeleteOutOf(total: Int) =
        this > 0 && (this == total || (this >= MIN_DELETIONS_TO_ASK && this * 2 > total))
}
