package com.pandulapeter.campfire.data.repository.implementation.base

import com.pandulapeter.campfire.data.model.DataState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal abstract class BaseLocalRemoteDataRepository<T>(
    private val loadDataFromLocalSource: suspend (databaseUrl: String) -> List<T>,
    private val loadDataFromRemoteSource: suspend (databaseUrl: String) -> List<T>,
    private val saveDataToLocalSource: suspend (databaseUrl: String, data: List<T>) -> Unit
) {
    private val _dataState = MutableStateFlow<DataState<List<T>>>(DataState.Failure(null))
    protected val dataState: Flow<DataState<List<T>>> = _dataState

    protected suspend fun loadData(databaseUrls: List<String>, isForceRefresh: Boolean) = coroutineScope {
        _dataState.run {
            if (value !is DataState.Loading) {
                var currentCache = value.data
                if (currentCache != null && !isForceRefresh) {
                    value = DataState.Idle(currentCache)
                } else {
                    value = DataState.Loading(currentCache)
                    if (currentCache == null) {
                        currentCache = databaseUrls.map { async { loadDataFromLocalSource(it) } }.awaitAll().flatten()
                        value = DataState.Loading(currentCache)
                    }
                    val remoteData = databaseUrls.map { databaseUrl ->
                        async {
                            try {
                                loadDataFromRemoteSource(databaseUrl).also {
                                    saveDataToLocalSource(databaseUrl, it)
                                }
                            } catch (exception: Exception) {
                                println(exception.message)
                                null
                            }
                        }
                    }.awaitAll().let { results ->
                        if (results.all { it == null }) null else results.filterNotNull().flatten()
                    }
                    value = if (remoteData == null) {
                        if (isForceRefresh) {
                            DataState.Failure(currentCache)
                        } else {
                            DataState.Idle(currentCache)
                        }
                    } else {
                        DataState.Idle(remoteData)
                    }
                }
            }
        }
    }
}