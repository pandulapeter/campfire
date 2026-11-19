/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.run

import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.sync.implementation.SyncIndexEntry
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import com.pandulapeter.campfire.data.sync.implementation.SyncOperation

/**
 * The documents a run's passes planned to delete on each side, and every one their planning indexes knew, by
 * folded name, so that [DeletionGuard] judges the run rather than the pass. A second pass happens exactly when another
 * device writes during the run, and that device may be deleting too: judged alone, each pass may delete less than half
 * of what it starts from while the two together delete most of the library. A pass is judged by what it and the passes
 * before it deleted, out of everything any of them knew, which is never more lenient than judging the pass alone.
 */
internal class RunDeletions {

    private val known = mutableSetOf<SyncKey>()
    private val local = mutableSetOf<SyncKey>()
    private val remote = mutableSetOf<SyncKey>()

    /** The question to ask before [plan], made from [planningIndex], moves; what it plans counts from then on. */
    fun check(
        plan: List<SyncOperation>,
        planningIndex: Map<SyncKey, SyncIndexEntry>,
        isLocalListingEmpty: Boolean,
        policy: SyncDeletionPolicy,
    ): SyncEngine.Result.DeletionsNeedConfirmation? {
        known += planningIndex.keys.filter { it.kind.isDocument }.map { it.folded() }
        plan.filter { it.key.kind.isDocument }.forEach { operation ->
            when (operation) {
                is SyncOperation.DeleteLocal -> local += operation.key.folded()
                is SyncOperation.DeleteRemote -> remote += operation.key.folded()
                else -> Unit
            }
        }
        return DeletionGuard.check(
            localDeletions = local.size,
            remoteDeletions = remote.size,
            indexSize = known.size,
            isLocalListingEmpty = isLocalListingEmpty,
            policy = policy,
        )
    }
}
