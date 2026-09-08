package com.pandulapeter.campfire.data.repository.implementation.base

import com.pandulapeter.campfire.data.model.DataState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal abstract class BaseLocalDataRepository<T>(
    val loadDataFromLocalSource: suspend () -> T,
    val saveDataToLocalSource: suspend (data: T) -> Unit
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
    protected suspend fun loadDataIfNeeded(): T? = mutex.withLock {
        _dataState.run {
            value.data ?: run {
                value = DataState.Loading(null)
                try {
                    loadDataFromLocalSource().also { value = DataState.Idle(it) }
                } catch (exception: Exception) {
                    println(exception.message)
                    value = DataState.Failure(null)
                    null
                }
            }
        }
    }

    protected suspend fun saveData(data: T) = _dataState.run {
        value = DataState.Loading(data)
        value = try {
            saveDataToLocalSource(data)
            DataState.Idle(data)
        } catch (exception: Exception) {
            println(exception.message)
            // The change is kept in memory even though it could not be written: undoing it under the user would be
            // more surprising than a preference that is lost when the app is restarted.
            DataState.Failure(data)
        }
    }
}
