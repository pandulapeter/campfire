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
import com.pandulapeter.campfire.data.sync.implementation.SyncEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

/**
 * Tells the account's list about this device's renames and stops as soon as they are made, rather than at the next run,
 * so that another device's run does not find a renamed item missing in the meantime. Best effort: whatever this cannot
 * write stays in the journal, and the run applies it ([SharedCopySync]). The two never apply at once: each holds
 * [SharedLinksJournal.whileApplying] from loading the entries until they are removed.
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

    /** Applies the journal to the list in [folder] without waiting for it; the caller has nothing to do with the result. */
    fun applySoon(folder: SyncFolder) {
        scope.launch { apply(folder) }
    }

    /**
     * Applies the journal to the list in [folder], while no run's step applies it ([SharedLinksJournal.whileApplying]).
     * Answers what stopped it where something was thrown; whatever stays in the journal, the caller reads there.
     */
    suspend fun apply(folder: SyncFolder): Exception? = journal.whileApplying {
        val changes = SharedCopyChanges()
        val applied = mutableListOf<SharedLinksJournal.Entry>()
        val failure = failureOf("apply the shared links journal") {
            val pending = journal.load()
            // Only a stop by name or of everything needs the list to say which shares it stops; the final edit reads it
            // again, and removes by id, so a list that changes in between is still edited right.
            val document = if (pending.any { it is SharedLinksJournal.Entry.StopItem || it == SharedLinksJournal.Entry.StopAll }) {
                when (val read = registry.read(folder)) {
                    is SharedLinksRegistry.Read.Readable -> read.document
                    is SharedLinksRegistry.Read.FromNewerVersion -> return@whileApplying null
                }
            } else {
                SharedLinksDocument()
            }
            pending.forEach { entry ->
                changes.takeFromJournal(entry, folder, document)
                applied += entry
            }
        }
        if (applied.isEmpty()) return@whileApplying failure
        // What was applied before a failure is written all the same: a stopped share's copy is gone, so its entry must go.
        val writeFailure = failureOf("write the shared links journal to the list") {
            val edit = registry.edit(folder) { current ->
                SharedLinksRegistry.Change(document = changes.applyTo(current).takeIf { it != current }, result = Unit)
            }
            if (edit is SharedLinksRegistry.Edit.Done) {
                journal.remove(applied)
                environment.logger.log("Sharing: ${applied.size} noted renames and stops applied to the list")
            }
        }
        failure ?: writeFailure
    }

    private inline fun failureOf(action: String, block: () -> Unit): Exception? = try {
        block()
        null
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        environment.logger.log("Could not $action: ${exception::class.simpleName}")
        exception
    }
}

/**
 * Takes [entry] of this device's journal into these changes, resolved against [document], the list as read. A stop
 * deletes each copy it stops before its entry goes, as Stop sharing does: an entry removed while its copy stays would
 * leave a working link that no screen shows any more. A stop that finds nothing to stop has nothing to do.
 */
internal suspend fun SharedCopyChanges.takeFromJournal(entry: SharedLinksJournal.Entry, folder: SyncFolder, document: SharedLinksDocument) {
    stoppedBy(entry, document).forEach { shareId -> folder.deleteSharedCopy(shareId) }
    take(entry, document)
}
