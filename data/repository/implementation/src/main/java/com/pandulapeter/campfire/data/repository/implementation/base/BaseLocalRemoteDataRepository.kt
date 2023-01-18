package com.pandulapeter.campfire.data.repository.implementation.base

import com.pandulapeter.campfire.data.model.DataState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal abstract class BaseLocalRemoteDataRepository<T>(
    private val loadDataFromLocalSource: suspend (databaseUrl: String) -> List<T>,
    private val loadDataFromRemoteSource: suspend (databaseUrl: String) -> List<T>,
    private val saveDataToLocalSource: suspend (databaseUrl: String, data: List<T>) -> Unit,
    private val deleteDataFromLocalSource: suspend () -> Unit
) {
    private val _dataState = MutableStateFlow<DataState<MutableMap<String, List<T>>>>(DataState.Failure(null))
    protected val dataState: Flow<DataState<Map<String, List<T>>>> = _dataState.map {
        when (it) {
            is DataState.Failure -> DataState.Failure(it.data?.toMap())
            is DataState.Idle -> DataState.Idle(it.data.toMap())
            is DataState.Loading -> DataState.Loading(it.data?.toMap())
        }
    }
    private val scope = object : CoroutineScope {
        override val coroutineContext = SupervisorJob() + Dispatchers.Default
    }

    abstract fun List<T>?.isValid(): Boolean

    protected suspend fun loadData(databaseUrls: List<String>, isForceRefresh: Boolean) = with(scope) {
        _dataState.run {
            if (value !is DataState.Loading) {
                val currentCache = databaseUrls.associateWith { value.data?.get(it).orEmpty() }.toMutableMap()
                if (currentCache.values.all { it.isValid() } && !isForceRefresh) {
                    value = DataState.Idle(currentCache)
                } else {
                    value = DataState.Loading(currentCache)
                    databaseUrls.filter { !currentCache[it].isValid() }.map { databaseUrl ->
                        async {
                            loadDataFromLocalSource(databaseUrl).also { data ->
                                if (data.isValid()) {
                                    currentCache[databaseUrl] = data
                                    value.data.let {
                                        if (it == null) {
                                            value = DataState.Loading(mutableMapOf(databaseUrl to data))
                                        } else {
                                            it[databaseUrl] = data
                                        }
                                    }
                                }
                            }
                        }
                    }.awaitAll()
                    value = DataState.Loading(currentCache)
                    var hasErrorHappened = false
                    databaseUrls.map { databaseUrl ->
                        async {
                            try {
                                loadDataFromRemoteSource(databaseUrl).also { data ->
                                    if (data.isValid()) {
                                        currentCache[databaseUrl] = data
                                        value.data.let {
                                            if (it == null) {
                                                value = DataState.Loading(mutableMapOf(databaseUrl to data))
                                            } else {
                                                it[databaseUrl] = data
                                            }
                                        }
                                        saveDataToLocalSource(databaseUrl, data)
                                    }
                                }
                            } catch (exception: Exception) {
                                hasErrorHappened = true
                                println(exception.message)
                                emptyList()
                            }
                        }
                    }.awaitAll()
                    value = if (currentCache.values.any { !it.isValid() } || (hasErrorHappened && isForceRefresh)) {
                        println("Failure! ${this@BaseLocalRemoteDataRepository::class.java}")
                        DataState.Failure(currentCache)
                    } else {
                        DataState.Idle(currentCache)
                    }
                }
            }
        }
    }

    protected suspend fun deleteLocalData() = _dataState.run {
        if (value !is DataState.Loading) {
            value = DataState.Loading(value.data)
            deleteDataFromLocalSource()
            value = DataState.Failure(null)
        }
    }
}