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

import com.pandulapeter.campfire.data.model.library.LibraryFiles

/**
 * What one run of [SharedCopySync] changes in the registry, kept as operations rather than as the document it read: the
 * edit that writes them reads the registry again on every attempt, and each operation applies as well to a list another
 * device changed in between. Each is idempotent, and one naming a share that is gone changes nothing.
 */
internal class SharedCopyChanges {

    private val renames = mutableListOf<Pair<String, String>>()
    private val removals = mutableSetOf<String>()
    private val updates = mutableMapOf<String, (SharedLinksEntry) -> SharedLinksEntry>()

    /** The shared item [from] is now the library file [to], matched by any case or Unicode form of the name. */
    fun rename(from: String, to: String) {
        renames += from to to
    }

    fun remove(shareId: String) {
        removals += shareId
    }

    /** Changes the entry [shareId] by [change], after whatever was asked of it before. */
    fun update(shareId: String, change: (SharedLinksEntry) -> SharedLinksEntry) {
        val previous = updates[shareId]
        updates[shareId] = if (previous == null) change else { entry -> change(previous(entry)) }
    }

    /** Renames first, so that an item is looked up under the name it has now, then removals, then updates. */
    fun applyTo(document: SharedLinksDocument) = document.copy(
        links = document.links
            .map { entry ->
                renames.fold(entry) { renamed, (from, to) ->
                    if (LibraryFiles.isSameLibraryName(renamed.fileName, from)) renamed.copy(fileName = to) else renamed
                }
            }
            .filterNot { it.shareId in removals }
            .map { entry -> updates[entry.shareId]?.invoke(entry) ?: entry },
    )
}
