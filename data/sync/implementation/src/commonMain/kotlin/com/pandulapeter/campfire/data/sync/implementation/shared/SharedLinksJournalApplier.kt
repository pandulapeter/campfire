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

import com.pandulapeter.campfire.data.model.recovering
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncFolder
import com.pandulapeter.campfire.data.sync.implementation.SyncEnvironment
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

/**
 * Tells the account's list about this device's renames and stops as soon as they are made, rather than at the next run,
 * so that another device's run does not find a renamed item missing in the meantime. Best effort: whatever this cannot
 * write stays in the journal, and the run applies it ([SharedCopySync]). Both may apply the same entries, which is
 * harmless, since each is idempotent.
 *
 * The whole journal is applied, in order, never only its newest entry: a rename made offline and another made of the
 * same item once back online only lead to the item's name together.
 */
@Single
internal class SharedLinksJournalApplier(
    private val journal: SharedLinksJournal,
    private val registry: SharedLinksRegistry,
    private val environment: SyncEnvironment,
) {

    private val scope = environment.scopeFor("shared links")

    /** Keeps two applications started in a row from deleting the same copies twice. */
    private val mutex = Mutex()

    /** Applies the journal to the list in [folder] without waiting for it; the caller has nothing to do with the result. */
    fun applySoon(folder: SyncFolder) {
        scope.launch { apply(folder) }
    }

    suspend fun apply(folder: SyncFolder) = mutex.withLock {
        val changes = SharedCopyChanges()
        val applied = mutableListOf<SharedLinksJournal.Entry>()
        environment.logger.recovering(
            describe = { "Could not apply the shared links journal: ${it::class.simpleName}" },
            fallback = {},
        ) {
            journal.load().forEach { entry ->
                changes.takeFromJournal(entry, folder)
                applied += entry
            }
        }
        if (applied.isEmpty()) return@withLock
        // What was applied before a failure is written all the same: a stopped share's copy is gone, so its entry must go.
        environment.logger.recovering(
            describe = { "Could not write the shared links journal to the list: ${it::class.simpleName}" },
            fallback = {},
        ) {
            val edit = registry.edit(folder) { current ->
                SharedLinksRegistry.Change(document = changes.applyTo(current).takeIf { it != current }, result = Unit)
            }
            if (edit is SharedLinksRegistry.Edit.Done) journal.remove(applied)
        }
    }
}

/**
 * Takes [entry] of this device's journal into these changes. A stop deletes the copy before its entry goes, as Stop
 * sharing does: an entry removed while its copy stays would leave a working link that no screen shows any more.
 */
internal suspend fun SharedCopyChanges.takeFromJournal(entry: SharedLinksJournal.Entry, folder: SyncFolder) {
    if (entry is SharedLinksJournal.Entry.Stop) folder.deleteSharedFile(entry.shareId)
    take(entry)
}
