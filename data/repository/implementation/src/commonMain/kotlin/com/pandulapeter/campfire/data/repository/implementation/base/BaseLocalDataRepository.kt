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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A repository backed by one local source that is read as a whole and cached in memory.
 *
 * Writing is *not* part of the shape: songs and setlists are one file each, so a change writes that file and updates
 * the cached list, rather than persisting the list. Only the preferences are saved as a whole, through [writeData].
 */
internal abstract class BaseLocalDataRepository<T> {

    // Loading rather than Failure, so that "nothing has been read yet" is not reported to the UI as an error.
    private val _dataState = MutableStateFlow<DataState<T>>(DataState.Loading(null))
    protected val dataState: Flow<DataState<T>> = _dataState

    // The repositories are asked for their data in parallel (see LoadScreenDataUseCase), so a second caller has to
    // wait for the first one's read instead of finding a load in progress and coming away with nothing.
    private val mutex = Mutex()
    private var isPublishingPartialData = false

    /** Reads everything this repository caches, from its own local source. */
    protected abstract suspend fun loadDataFromLocalSource(): T

    /**
     * The data, read from the local source the first time it is asked for. Null if that read failed: storage that
     * cannot be read is reported rather than waited on, so that the UI never keeps a loading state forever.
     */
    protected suspend fun loadDataIfNeeded(): T? = mutex.withLock { _dataState.value.data ?: read() }

    /** Reads the local source again even if there already is data, which is what a refresh does. */
    protected suspend fun reloadData(): T? = mutex.withLock { read() }

    /**
     * Replaces the cached data without writing anything, for changes that persisted themselves file by file. The
     * transform is applied to the state as it is at that moment, so two changes that land at once (a save in the
     * editor and the rescan a sync run finished with) build on each other instead of the later one overwriting the
     * earlier. [DataState.data] is null while nothing has been read yet.
     */
    protected fun updateData(transform: (T?) -> T) = _dataState.update { DataState.Idle(transform(it.data)) }

    /**
     * Publishes what a read has put together so far, so that a slow one fills the screen as it goes. It is still a
     * [DataState.Loading], since none of it is the whole answer yet.
     *
     * Only ever published while there is nothing on screen: a re-read has the previous data up, and replacing that
     * with a partial one would make the list shrink and fill up again under the user. Half a library beats an empty
     * screen; it does not beat the library.
     */
    protected fun publishPartialData(data: T) {
        if (isPublishingPartialData) _dataState.value = DataState.Loading(data)
    }

    /**
     * Publishes [data], then persists it as a whole.
     *
     * It is published as [DataState.Idle] right away rather than as a [DataState.Loading] that the write then
     * resolves: what is being written is already what every reader should be showing, and it stays that way even
     * when the write fails. A [DataState.Loading] here would say "nothing has been read yet" to whoever reads this
     * state for that — and a write is a suspending call, so it would say it for as long as the storage takes.
     */
    protected suspend fun writeData(data: T, persist: suspend (T) -> Unit) = _dataState.run {
        value = DataState.Idle(data)
        value = try {
            persist(data)
            DataState.Idle(data)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println(exception.message)
            // The change is kept in memory even though it could not be written: undoing it under the user would be
            // more surprising than a preference that is lost when the app is restarted.
            DataState.Failure(data)
        }
    }

    private suspend fun read(): T? = _dataState.run {
        // The data that is already on screen stays there while the re-read runs, so a refresh does not blank the list.
        val previousData = value.data
        isPublishingPartialData = previousData == null
        value = DataState.Loading(previousData)
        try {
            loadDataFromLocalSource().also { value = DataState.Idle(it) }
        } catch (exception: CancellationException) {
            // A read the caller gave up on is not a read that failed: the data on screen stays what it was, and the
            // next caller reads again. Whatever a partial publish put up is dropped rather than kept, or half a
            // library would sit there as the finished one and nothing would ever read the rest of it.
            value = DataState.Idle(previousData ?: throw exception)
            throw exception
        } catch (exception: Exception) {
            println(exception.message)
            value = DataState.Failure(previousData)
            null
        } finally {
            isPublishingPartialData = false
        }
    }
}
