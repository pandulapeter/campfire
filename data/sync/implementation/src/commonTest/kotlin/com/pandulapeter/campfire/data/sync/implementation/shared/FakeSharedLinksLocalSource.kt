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

import com.pandulapeter.campfire.data.source.local.api.SharedLinksLocalSource

/** The shared links' mirror and journal held in memory; [onLoadJournal] runs before a read of the journal answers. */
internal class FakeSharedLinksLocalSource(
    var mirror: String? = null,
    var journal: String? = null,
    var onLoadJournal: () -> Unit = {},
) : SharedLinksLocalSource {

    override suspend fun loadSharedLinksMirror() = mirror

    override suspend fun saveSharedLinksMirror(document: String?) {
        mirror = document
    }

    override suspend fun loadSharedLinksJournal(): String? {
        onLoadJournal()
        return journal
    }

    override suspend fun saveSharedLinksJournal(document: String?) {
        journal = document
    }
}
