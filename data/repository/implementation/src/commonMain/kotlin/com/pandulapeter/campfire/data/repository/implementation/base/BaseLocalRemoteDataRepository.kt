package com.pandulapeter.campfire.data.repository.implementation.base

import com.pandulapeter.campfire.data.model.DataState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal abstract class BaseLocalRemoteDataRepository<T>(
    private val loadDataFromLocalSource: suspend (databaseUrl: String) -> List<T>,
    private val loadDataFromRemoteSource: suspend (databaseUrl: String) -> List<T>,
    private val saveDataToLocalSource: suspend (databaseUrl: String, data: List<T>) -> Unit
) {
    private var isFirstLoading = true
    private var isLoadInProgress = false
    // Loading rather than Failure, so that "nothing has been read yet" is not reported to the UI as an error.
    private val _dataState = MutableStateFlow<DataState<Map<String, List<T>>>>(DataState.Loading(null))
    protected val dataState: Flow<DataState<Map<String, List<T>>>> = _dataState
    private val scope = object : CoroutineScope {
        override val coroutineContext = SupervisorJob() + Dispatchers.Default
    }

    abstract fun List<T>?.isValid(): Boolean

    /**
     * @return Whether every requested database ended up with valid data and nothing failed on the way there. A
     *   caller that was triggered by the user turns a false into a message, the rest let it pass silently.
     */
    protected suspend fun loadData(databaseUrls: List<String>, isForceRefresh: Boolean): Boolean {
        // A load that is already running is left to finish rather than restarted: the two would be writing to the
        // same state at the same time, and what the second one wants is what the first one is already fetching.
        if (isLoadInProgress) return true
        isLoadInProgress = true
        return try {
            load(databaseUrls, isForceRefresh)
        } finally {
            isLoadInProgress = false
        }
    }

    /**
     * Every database is loaded in parallel, first from the local source and then from the remote one, and each result
     * is published as soon as it arrives, so that the songs of the first database appear while the rest are still on
     * their way. The state is always replaced, never mutated in place: those parallel loads would otherwise be
     * writing to one shared map at the same time.
     */
    private suspend fun load(databaseUrls: List<String>, isForceRefresh: Boolean): Boolean = with(scope) {
        // Only the requested databases are kept, so that the data of a database that was turned off goes with it.
        val cache = databaseUrls.associateWith { _dataState.value.data?.get(it).orEmpty() }

        fun Map<String, List<T>>.isComplete() = !isFirstLoading && !isForceRefresh && values.all { it.isValid() }

        if (cache.isComplete()) {
            _dataState.value = DataState.Idle(cache)
            return@with true
        }
        _dataState.value = DataState.Loading(cache)
        databaseUrls.filterNot { cache[it].isValid() }
            .map { databaseUrl -> async { publish(databaseUrl, loadFromLocalSource(databaseUrl)) } }
            .awaitAll()
        currentData().let { dataFromLocalSource ->
            if (dataFromLocalSource.isComplete()) {
                _dataState.value = DataState.Idle(dataFromLocalSource)
                return@with true
            }
        }
        isFirstLoading = false
        var hasErrorHappened = false
        databaseUrls.map { databaseUrl ->
            async {
                try {
                    loadDataFromRemoteSource(databaseUrl).let { data ->
                        if (data.isValid()) {
                            publish(databaseUrl, data)
                            saveToLocalSource(databaseUrl, data)
                        }
                    }
                } catch (exception: Exception) {
                    hasErrorHappened = true
                    println(exception.message)
                }
            }
        }.awaitAll()
        val data = currentData()
        // A database that is still without data after all this is a failure whether or not anything was thrown:
        // an empty response is as unusable as no response.
        val isSuccessful = !hasErrorHappened && data.values.all { it.isValid() }
        _dataState.value = if (isSuccessful) DataState.Idle(data) else DataState.Failure(data)
        isSuccessful
    }

    // Only ever holds the databases of the ongoing load: the state was replaced with exactly those when it started.
    private fun currentData() = _dataState.value.data.orEmpty()

    /** Storage that cannot be read is the same as storage that holds nothing: the remote source is asked next. */
    private suspend fun loadFromLocalSource(databaseUrl: String) = try {
        loadDataFromLocalSource(databaseUrl)
    } catch (exception: Exception) {
        println(exception.message)
        emptyList()
    }

    /** Data that arrived but could not be cached is still data the user can use, so this failure is not reported. */
    private suspend fun saveToLocalSource(databaseUrl: String, data: List<T>) = try {
        saveDataToLocalSource(databaseUrl, data)
    } catch (exception: Exception) {
        println(exception.message)
    }

    private fun publish(databaseUrl: String, data: List<T>) {
        if (!data.isValid()) return
        _dataState.update { DataState.Loading(it.data.orEmpty() + (databaseUrl to data)) }
    }
}
