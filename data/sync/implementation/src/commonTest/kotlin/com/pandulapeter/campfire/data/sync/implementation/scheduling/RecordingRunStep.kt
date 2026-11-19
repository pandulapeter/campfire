/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.scheduling

import com.pandulapeter.campfire.data.source.remote.api.sync.SyncFolder
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteSharedFile
import com.pandulapeter.campfire.data.sync.implementation.SyncIndexEntry
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * A run step of an app that is not Campfire, which notes into [events] when it runs and is forgotten, and answers [run]
 * with [answer] (or throws what it gives). Where [runLock] tries the run lock, every call notes whether it found the lock
 * held ([heldLock]).
 */
internal class RecordingRunStep(
    override val id: String,
    private val events: MutableList<String>,
    private val runLock: (suspend (suspend () -> Unit) -> Unit)? = null,
    private val answer: () -> Boolean = { true },
) : SyncRunStep {

    override val documentNames = setOf("$id.json")

    /** The index each run handed over. */
    val indexes = mutableListOf<Map<SyncKey, SyncIndexEntry>>()

    /** Whether the run lock was taken by someone else, one value per [run] and [forget]. */
    val heldLock = mutableListOf<Boolean>()

    override suspend fun run(
        folder: SyncFolder,
        index: Map<SyncKey, SyncIndexEntry>,
        documentRevisions: Map<String, String>,
        sharedFiles: List<RemoteSharedFile>,
    ): Boolean {
        events += "run $id"
        indexes += index
        noteLock()
        return answer()
    }

    override suspend fun onDocumentChanged(folder: SyncFolder) {
        events += "read $id"
    }

    override suspend fun forget() {
        events += "forget $id"
        noteLock()
    }

    /** Tries the lock from another coroutine: one that cannot have it by the time this one yields found it held. */
    private suspend fun noteLock() {
        val tryLock = runLock ?: return
        coroutineScope {
            val attempt = launch { tryLock {} }
            yield()
            heldLock += attempt.isActive
            attempt.cancel()
        }
    }
}
