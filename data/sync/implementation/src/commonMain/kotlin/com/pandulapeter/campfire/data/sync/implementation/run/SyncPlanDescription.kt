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

import com.pandulapeter.campfire.data.sync.implementation.SyncOperation

/**
 * What a pass of a run sets out to do, as the process log says it: how many files of each kind of operation, never
 * their names, which would name songs.
 */
internal fun List<SyncOperation>.describedForLog(): String {
    if (isEmpty()) return "nothing to move"
    val counts = listOf(
        "downloads" to count { it is SyncOperation.Download },
        "uploads" to count { it is SyncOperation.Upload },
        "local deletions" to count { it is SyncOperation.DeleteLocal },
        "remote deletions" to count { it is SyncOperation.DeleteRemote },
        "conflicts" to count { it is SyncOperation.Resolve },
        "forgotten" to count { it is SyncOperation.Forget },
    )
    return counts.filter { (_, count) -> count > 0 }.joinToString { (name, count) -> "$count $name" }
}
