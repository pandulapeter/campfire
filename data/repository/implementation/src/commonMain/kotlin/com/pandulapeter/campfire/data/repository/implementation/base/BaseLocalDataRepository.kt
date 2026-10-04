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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A repository backed by one local source that is read as a whole and cached in memory.
 *
 * Writing is *not* part of the shape: songs and setlists are one file each, so a change writes that file and updates
 * the cached list, rather than persisting the list. Only the preferences are saved as a whole, through [writeData].
 *
 * A change made through [updateData] while a read is running is on disk already, but the read may have listed the
 * directory before it got there, and publishing that result as it is would drop the change from the list until the
 * next rescan. A read that saw one land reads again once it has published, since a second directory listing is the one
 * answer that is right whatever the change was — but only once: a sync run refreshes the files it wrote about once a
 * second for as long as it runs, and a scan slower than that would otherwise never finish. The changes that land
 * during that second read are applied onto its result instead, which every caller of [updateData] makes safe: each
 * change replaces or drops the entries of the files it names with what is on disk now, so applying it again to a
 * list that already has it changes nothing.
 */
internal abstract class BaseLocalDataRepository<T> {

    // Loading rather than Failure, so that "nothing has been read yet" is not reported to the UI as an error.
    private val _dataState = MutableStateFlow<DataState<T>>(DataState.Loading(null))
    protected val dataState: Flow<DataState<T>> = _dataState

    // The repositories are asked for their data in parallel (see LoadScreenDataUseCase), so a second caller has to
    // wait for the first one's read instead of finding a load in progress and coming away with nothing.
    private val mutex = Mutex()
    private var isPublishingPartialData = false

    /**
     * Held while the storage is written to, so that the writes reach it one at a time. A lock of its own rather than
     * [mutex]: a read is no reason for a change to wait, and a change is published before it takes this one.
     */
    private val writeMutex = Mutex()

    /** What the last write that succeeded put into the storage. Only touched while [writeMutex] is held. */
    private var lastPersistedData: T? = null

    /**
     * Set by a read that failed and cleared by one that succeeded, rather than read off the state: a write of the
     * preferences that failed is a [DataState.Failure] too, and reading those again would replace the change being
     * kept in memory with the older document still on disk.
     */
    private var hasReadFailed = false

    /**
     * Every change [updateData] applied since the running read started, in order, or null while no read is running.
     * A state flow for its atomic update, since [updateData] is called from any thread and does not suspend.
     */
    private val transformsDuringRead = MutableStateFlow<List<(T?) -> T>?>(null)

    /** Reads everything this repository caches, from its own local source. */
    protected abstract suspend fun loadDataFromLocalSource(): T

    /**
     * The data, read from the local source the first time it is asked for. Null if that read failed: storage that
     * cannot be read is reported rather than waited on, so that the UI never keeps a loading state forever.
     *
     * A read that failed is tried again by the next caller even when changes made since have put some data in the
     * cache: that data is only what those changes added, and returning it would pass a single new song off as the
     * whole library for as long as the app runs.
     *
     * A [DataState.Loading] is read again whatever it carries. Every read holds the lock this runs under, so one seen
     * from here is not a read in progress but the leftover of one that never finished, and the data in it is however
     * much of the library that read had got through.
     */
    protected suspend fun loadDataIfNeeded(): T? = mutex.withLock {
        _dataState.value.takeUnless { it is DataState.Loading || hasReadFailed }?.data ?: read()
    }

    /** Reads the local source again even if there already is data, which is what a refresh does. */
    protected suspend fun reloadData(): T? = mutex.withLock { read() }

    /**
     * Replaces the cached data without writing anything, for changes that persisted themselves file by file. The
     * transform is applied to the state as it is at that moment, so two changes that land at once (a save in the
     * editor and the rescan a sync run finished with) build on each other instead of the later one overwriting the
     * earlier. [DataState.data] is null while nothing has been read yet.
     *
     * A [DataState.Failure] stays one: the change is applied, but the read it is applied to is still the one that
     * failed, and the UI goes on reporting that. A [DataState.Loading] stays one too: what it carries is however much
     * of the library a read has got through, or nothing at all before the first one, and publishing that with the
     * change as [DataState.Idle] would pass it off as the finished library to everyone who waits for one (the cover
     * cache's prune, the launch's deep link, the end of the loading state).
     */
    protected fun updateData(transform: (T?) -> T) {
        transformsDuringRead.update { it?.plus(transform) }
        _dataState.update {
            when (it) {
                is DataState.Failure -> DataState.Failure(transform(it.data))
                is DataState.Loading -> DataState.Loading(transform(it.data))
                else -> DataState.Idle(transform(it.data))
            }
        }
    }

    /**
     * Publishes what a read has put together so far, so that a slow one fills the screen as it goes. It is still a
     * [DataState.Loading], since none of it is the whole answer yet.
     *
     * Only ever published while there is nothing on screen: a re-read has the previous data up, and replacing that
     * with a partial one would make the list shrink and fill up again under the user. Half a library beats an empty
     * screen; it does not beat the library.
     *
     * The changes [updateData] recorded since the read started are applied onto every batch, since the read builds
     * its batches without them and a change made to a song already on screen would otherwise vanish until the read
     * ends. They are read inside the atomic update, so that a change landing between the two is not overwritten by a
     * batch that lacks it: [updateData] records before it publishes, and the retry sees both.
     */
    protected fun publishPartialData(data: T) {
        if (!isPublishingPartialData) return
        _dataState.update { DataState.Loading(transformsDuringRead.value.orEmpty().fold(data) { result, transform -> transform(result) }) }
    }

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
        _dataState.value = DataState.Idle(data)
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
        _dataState.update { current ->
            val data = current.data ?: return@update current
            val transformed = transform(data)
            changed = transformed.takeIf { it != data }
            if (changed == null) current else DataState.Idle(transformed)
        }
        persistLatest(changed ?: return, persist)
    }

    private suspend fun persistLatest(data: T, persist: suspend (T) -> Unit) {
        writeMutex.withLock {
            val latestData = _dataState.value.data ?: data
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
                    _dataState.update { if (it.data == latestData) DataState.Failure(latestData) else it }
                }
            }
        }
    }

    private suspend fun read(): T? = try {
        var rereads = 0
        var data = readOnce()
        while (data != null && !transformsDuringRead.value.isNullOrEmpty() && rereads++ < MAX_REREADS) {
            data = readOnce()
        }
        val transforms = transformsDuringRead.getAndUpdate { null }.orEmpty()
        if (data == null || transforms.isEmpty()) {
            data
        } else {
            // Applied onto the state as it is now rather than onto the result: a change that landed after the result
            // was published is in the state already and applies again as a no-op, while one that lands after the
            // recording stops is applied by updateData alone, and replacing the state wholesale would drop it.
            _dataState.updateAndGet { current ->
                val published: T = current.data ?: data
                val replayed = transforms.fold(published) { result, transform -> transform(result) }
                if (current is DataState.Failure) DataState.Failure(replayed) else DataState.Idle(replayed)
            }.data
        }
    } finally {
        transformsDuringRead.value = null
    }

    private suspend fun readOnce(): T? = _dataState.run {
        // The data that is already on screen stays there while the re-read runs, so a refresh does not blank the list.
        val previousData = value.data
        isPublishingPartialData = previousData == null
        value = DataState.Loading(previousData)
        transformsDuringRead.value = emptyList()
        try {
            loadDataFromLocalSource().also {
                value = DataState.Idle(it)
                hasReadFailed = false
            }
        } catch (exception: CancellationException) {
            // A read the caller gave up on is not a read that failed: the data on screen stays what it was, and the
            // next caller reads again. Whatever a partial publish put up is dropped rather than kept, or half a
            // library would sit there as the finished one and nothing would ever read the rest of it - so a first read
            // goes back to having read nothing, which is also what sends the next caller to the local source. A re-read
            // publishes no partial data, and what the cache holds by now is the previous data plus the changes that
            // landed while it ran, which are on disk and stay.
            update { current -> if (previousData == null) DataState.Loading(null) else DataState.Idle(current.data ?: previousData) }
            throw exception
        } catch (exception: Exception) {
            println(exception.message)
            value = DataState.Failure(previousData)
            hasReadFailed = true
            null
        } finally {
            isPublishingPartialData = false
        }
    }

    private companion object {

        /** How many times a read goes again for changes that landed while it ran before it applies them instead. */
        const val MAX_REREADS = 1
    }
}
