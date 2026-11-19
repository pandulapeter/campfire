/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.library

/**
 * Every [SyncCollection] a library is made of, in its own type so that it is never injected as a `List`. Ids are
 * unique: a key read back from the sync index or a remote path is resolved through [byId].
 */
class SyncCollections(
    val all: List<SyncCollection>,
) : Iterable<SyncCollection> by all {

    init {
        require(all.distinctBy { it.id }.size == all.size) { "Collection ids must be unique: $all" }
    }

    fun byId(id: String) = all.firstOrNull { it.id == id }
}
