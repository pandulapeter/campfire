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
import com.pandulapeter.campfire.data.model.share.SharedLinkKind

/**
 * What one run of [SharedCopySync] changes in the registry, kept as operations rather than as the document it read: the
 * edit that writes them reads the registry again on every attempt, and each operation applies as well to a list another
 * device changed in between. Each is idempotent, and one naming a share that is gone changes nothing.
 */
internal class SharedCopyChanges {

    private val renames = mutableListOf<SharedLinksJournal.Entry.Rename>()
    private val removals = mutableSetOf<String>()
    private val updates = mutableMapOf<String, (SharedLinksEntry) -> SharedLinksEntry>()

    /**
     * The shared item [from] is now the library file [to]: the share [shareId] where it is known, else every entry
     * named [from] by any case or Unicode form of the name.
     */
    fun rename(from: String, to: String, shareId: String?) {
        renames += SharedLinksJournal.Entry.Rename(from = from, to = to, shareId = shareId)
    }

    fun remove(shareId: String) {
        removals += shareId
    }

    /**
     * What [entry] of this device's journal changes in the registry, resolved against [document] as the entries taken
     * before it leave it; deleting a stopped share's copy is the caller's.
     */
    fun take(entry: SharedLinksJournal.Entry, document: SharedLinksDocument) = when (entry) {
        is SharedLinksJournal.Entry.Rename -> rename(from = entry.from, to = entry.to, shareId = entry.shareId)
        else -> stoppedBy(entry, document).forEach(::remove)
    }

    /**
     * The shares the stop [entry] of this device's journal stops in [document], as the entries taken before it leave
     * it: so an item deleted after a rename is found under the name it had when deleted. None for a rename, and none
     * where the list names no such share, which leaves nothing to do.
     */
    fun stoppedBy(entry: SharedLinksJournal.Entry, document: SharedLinksDocument) = when (entry) {
        is SharedLinksJournal.Entry.Rename -> emptyList()
        is SharedLinksJournal.Entry.Stop -> listOf(entry.shareId)
        is SharedLinksJournal.Entry.StopItem -> listOfNotNull(
            SharedLinkKind.fromId(entry.kind)?.let { kind -> applyTo(document).entryFor(kind, entry.fileName)?.shareId },
        )
        SharedLinksJournal.Entry.StopAll -> applyTo(document).links.map { it.shareId }
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
                renames.fold(entry) { renamed, rename ->
                    val isRenamed = rename.shareId?.let { it == renamed.shareId } ?: LibraryFiles.isSameLibraryName(renamed.fileName, rename.from)
                    if (isRenamed) renamed.copy(fileName = rename.to) else renamed
                }
            }
            .filterNot { it.shareId in removals }
            .map { entry -> updates[entry.shareId]?.invoke(entry) ?: entry },
    )
}
