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
    private val _dataState = MutableStateFlow<DataState<Map<String, List<T>>>>(DataState.Failure(null))
    protected val dataState: Flow<DataState<Map<String, List<T>>>> = _dataState
    private val scope = object : CoroutineScope {
        override val coroutineContext = SupervisorJob() + Dispatchers.Default
    }

    abstract fun List<T>?.isValid(): Boolean

    /**
     * Every database is loaded in parallel, first from the local source and then from the remote one, and each result
     * is published as soon as it arrives, so that the songs of the first database appear while the rest are still on
     * their way. The state is always replaced, never mutated in place: those parallel loads would otherwise be
     * writing to one shared map at the same time.
     */
    protected suspend fun loadData(databaseUrls: List<String>, isForceRefresh: Boolean) = with(scope) {
        if (_dataState.value is DataState.Loading) return@with
        // Only the requested databases are kept, so that the data of a database that was turned off goes with it.
        val cache = databaseUrls.associateWith { _dataState.value.data?.get(it).orEmpty() }

        fun Map<String, List<T>>.isComplete() = !isFirstLoading && !isForceRefresh && values.all { it.isValid() }

        if (cache.isComplete()) {
            _dataState.value = DataState.Idle(cache)
            return@with
        }
        _dataState.value = DataState.Loading(cache)
        databaseUrls.filterNot { cache[it].isValid() }
            .map { databaseUrl -> async { publish(databaseUrl, loadDataFromLocalSource(databaseUrl)) } }
            .awaitAll()
        currentData().let { dataFromLocalSource ->
            if (dataFromLocalSource.isComplete()) {
                _dataState.value = DataState.Idle(dataFromLocalSource)
                return@with
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
                            saveDataToLocalSource(databaseUrl, data)
                        }
                    }
                } catch (exception: Exception) {
                    hasErrorHappened = true
                    println(exception.message)
                }
            }
        }.awaitAll()
        val data = currentData()
        _dataState.value = if (data.values.any { !it.isValid() } || (hasErrorHappened && isForceRefresh)) {
            DataState.Failure(data)
        } else {
            DataState.Idle(data)
        }
    }

    // Only ever holds the databases of the ongoing load: the state was replaced with exactly those when it started.
    private fun currentData() = _dataState.value.data.orEmpty()

    private fun publish(databaseUrl: String, data: List<T>) {
        if (!data.isValid()) return
        _dataState.update { DataState.Loading(it.data.orEmpty() + (databaseUrl to data)) }
    }
}
