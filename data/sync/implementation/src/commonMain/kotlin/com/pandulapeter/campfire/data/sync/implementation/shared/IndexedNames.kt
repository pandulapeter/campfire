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

import com.pandulapeter.campfire.data.model.library.LibraryCollections
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.library.SyncCollection
import com.pandulapeter.campfire.data.sync.implementation.SyncIndexEntry
import com.pandulapeter.campfire.data.sync.implementation.SyncKey

/** The run's index by the identity of each name, since the registry and setlists may spell a file differently. */
internal class IndexedNames(index: Map<SyncKey, SyncIndexEntry>) : SharedCopyNames {

    private val entries by lazy { index.entries.associateBy { (key, _) -> key.kind to LibraryFiles.identityKey(key.name) } }

    fun find(kind: SyncCollection, name: String) = entries[kind to LibraryFiles.identityKey(name)]

    override fun localNameOf(kind: SyncCollection, name: String) = find(kind, name)?.key?.name ?: name

    override fun holdsFile(name: String) = find(LibraryCollections.FILES, name) != null

    /** Whether the library held any picture at the end of the run, without which no song shows one. */
    val hasImages by lazy { entries.keys.any { (kind, _) -> kind == LibraryCollections.FILES } }
}
