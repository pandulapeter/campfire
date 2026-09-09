package com.pandulapeter.campfire.data.repository.implementation.base

import com.pandulapeter.campfire.data.model.DataState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A repository backed by one local source that is read as a whole and cached in memory.
 *
 * Writing is *not* part of the shape: songs and setlists are one file each, so a change writes that file and updates
 * the cached list, rather than persisting the list. Only the preferences are saved as a whole, through [writeData].
 */
internal abstract class BaseLocalDataRepository<T>(
    private val loadDataFromLocalSource: suspend () -> T
) {
    // Loading rather than Failure, so that "nothing has been read yet" is not reported to the UI as an error.
    private val _dataState = MutableStateFlow<DataState<T>>(DataState.Loading(null))
    protected val dataState: Flow<DataState<T>> = _dataState

    // The repositories are asked for their data in parallel (see LoadScreenDataUseCase), so a second caller has to
    // wait for the first one's read instead of finding a load in progress and coming away with nothing.
    private val mutex = Mutex()

    /**
     * The data, read from the local source the first time it is asked for. Null if that read failed: storage that
     * cannot be read is reported rather than waited on, so that the UI never keeps a loading state forever.
     */
    protected suspend fun loadDataIfNeeded(): T? = mutex.withLock { _dataState.value.data ?: read() }

    /** Reads the local source again even if there already is data, which is what a refresh does. */
    protected suspend fun reloadData(): T? = mutex.withLock { read() }

    /** Replaces the cached data without writing anything, for changes that persisted themselves file by file. */
    protected fun updateData(data: T) {
        _dataState.value = DataState.Idle(data)
    }

    /** Publishes [data], then persists it as a whole. */
    protected suspend fun writeData(data: T, persist: suspend (T) -> Unit) = _dataState.run {
        value = DataState.Loading(data)
        value = try {
            persist(data)
            DataState.Idle(data)
        } catch (exception: Exception) {
            println(exception.message)
            // The change is kept in memory even though it could not be written: undoing it under the user would be
            // more surprising than a preference that is lost when the app is restarted.
            DataState.Failure(data)
        }
    }

    private suspend fun read(): T? = _dataState.run {
        // The data that is already on screen stays there while the re-read runs, so a refresh does not blank the list.
        value = DataState.Loading(value.data)
        try {
            loadDataFromLocalSource().also { value = DataState.Idle(it) }
        } catch (exception: Exception) {
            println(exception.message)
            value = DataState.Failure(value.data)
            null
        }
    }
}
