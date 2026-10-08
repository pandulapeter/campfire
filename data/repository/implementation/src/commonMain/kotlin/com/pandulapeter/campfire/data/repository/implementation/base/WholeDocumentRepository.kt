/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation.base

import com.pandulapeter.campfire.data.model.DataState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A [BaseLocalDataRepository] whose data is one document, persisted as a whole: the preferences. The list repositories
 * extend the base directly, so that nothing can replace their cached library with a list of its own making.
 */
internal abstract class WholeDocumentRepository<T> : BaseLocalDataRepository<T>() {

    /**
     * Held while the storage is written to, so that the writes reach it one at a time. A lock of its own rather than the
     * base's read lock: a read is no reason for a change to wait, and a change is published before it takes this one.
     */
    private val writeMutex = Mutex()

    /** What the last write that succeeded put into the storage. Only touched while [writeMutex] is held. */
    private var lastPersistedData: T? = null

    /**
     * Publishes [data], then persists it as a whole.
     *
     * It is published as [DataState.Idle] right away rather than as a [DataState.Loading] that the write then
     * resolves: what is being written is already what every reader should be showing, and it stays that way even
     * when the write fails. A [DataState.Loading] here would say "nothing has been read yet" to whoever reads this
     * state for that — and a write is a suspending call, so it would say it for as long as the storage takes.
     *
     * It is also published *before* the storage is waited for, because the callers build each change on the state
     * they find: a change that stayed unpublished while an earlier one was still being written would be missing from
     * the next one. The storage is then written to one call at a time, and what a call writes is whatever is
     * published by the time the storage is free rather than what it was called with. That is what keeps the last
     * change the last thing written whichever thread each call came from, and it lets a burst of changes end in one
     * write instead of one each. A write that succeeded publishes nothing: there is nothing to say that the first
     * publish did not, and saying it again would put this call's data back over a change made since.
     */
    protected suspend fun writeData(data: T, persist: suspend (T) -> Unit) {
        updateState { DataState.Idle(data) }
        persistLatest(data, persist)
    }

    /**
     * [writeData] for a change rather than a whole document: [transform] is applied to the data published at this
     * moment, atomically, and the result is published and persisted the same way. A caller that built the whole
     * document itself would build it on whatever copy of the state it holds, and a copy that has not caught up with
     * the previous change yet - a flow a few hops downstream, collected on another dispatcher - would put that change
     * back. [transform] may run more than once when changes race, so it has to be pure.
     *
     * Nothing happens while there is no data, which is before the first read and after one that failed: there is
     * nothing for the change to apply to, and a document made of it alone would replace everything else in the store.
     * Nor when [transform] changes nothing, which would otherwise take a failed write's [DataState.Failure] off the
     * screen without anything having been written.
     */
    protected suspend fun transformAndWriteData(transform: (T) -> T, persist: suspend (T) -> Unit) {
        var changed: T? = null
        updateState { current ->
            val data = current.data ?: return@updateState current
            val transformed = transform(data)
            changed = transformed.takeIf { it != data }
            if (changed == null) current else DataState.Idle(transformed)
        }
        persistLatest(changed ?: return, persist)
    }

    private suspend fun persistLatest(data: T, persist: suspend (T) -> Unit) {
        writeMutex.withLock {
            val latestData = currentState.data ?: data
            if (latestData != lastPersistedData) {
                try {
                    persist(latestData)
                    lastPersistedData = latestData
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    println(exception.message)
                    // The change is kept in memory even though it could not be written: undoing it under the user would
                    // be more surprising than a preference that is lost when the app is restarted. Only if it is still
                    // the change on show, though - a newer one has a write of its own coming, and its own answer.
                    updateState { if (it.data == latestData) DataState.Failure(latestData) else it }
                }
            }
        }
    }
}
