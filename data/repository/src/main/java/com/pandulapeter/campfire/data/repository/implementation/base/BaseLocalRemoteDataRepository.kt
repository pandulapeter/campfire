package com.pandulapeter.campfire.data.repository.implementation.base

import com.pandulapeter.campfire.data.model.DataState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal abstract class BaseLocalRemoteDataRepository<T>(
    private val loadDataFromLocalSource: suspend (sheetUrl: String) -> T,
    private val loadDataFromRemoteSource: suspend (sheetUrl: String) -> T,
    private val saveDataToLocalSource: suspend (sheetUrl: String, data: T) -> Unit
) {
    private val _dataState = MutableStateFlow<DataState<T>>(DataState.Failure(null))
    protected val dataState: Flow<DataState<T>> = _dataState

    protected suspend fun loadData(sheetUrl: String, isForceRefresh: Boolean) = _dataState.run {
        if (value !is DataState.Loading) {
            var currentCache = value.data
            if (currentCache != null && !isForceRefresh) {
                value = DataState.Idle(currentCache)
            } else {
                value = DataState.Loading(currentCache)
                if (currentCache == null) {
                    currentCache = loadDataFromLocalSource(sheetUrl)
                    value = DataState.Loading(currentCache)
                }
                val remoteData = try {
                    loadDataFromRemoteSource(sheetUrl).also {
                        value = DataState.Loading(it)
                        saveDataToLocalSource(sheetUrl, it)
                    }
                } catch (exception: Exception) {
                    println(exception.message)
                    null
                }
                value = if (remoteData == null) {
                    if (currentCache == null || isForceRefresh) {
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