/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.shared

import com.pandulapeter.campfire.data.source.remote.api.sync.SyncFolder

/**
 * Deletes the shared copy [shareId], an id that no copy can have counting as one already gone. The folder refuses such
 * an id before any request ([SyncFolder.uploadSharedFile]), and the account's list, which anyone can edit by hand, may
 * hold one: thrown, it would stop every later stop of the journal and every Stop sharing everything at the same entry.
 */
internal suspend fun SyncFolder.deleteSharedCopy(shareId: String) {
    try {
        deleteSharedFile(shareId)
    } catch (_: IllegalArgumentException) {
        // No copy is named by it, so there is none to delete and nothing left that its link could serve.
    }
}
