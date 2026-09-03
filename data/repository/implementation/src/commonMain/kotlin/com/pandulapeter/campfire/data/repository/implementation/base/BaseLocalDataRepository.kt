package com.pandulapeter.campfire.data.repository.implementation.base

import com.pandulapeter.campfire.data.model.DataState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal abstract class BaseLocalDataRepository<T>(
    val loadDataFromLocalSource: suspend () -> T,
    val saveDataToLocalSource: suspend (data: T) -> Unit
) {
    private val _dataState = MutableStateFlow<DataState<T>>(DataState.Failure(null))
    protected val dataState: Flow<DataState<T>> = _dataState

    protected suspend fun loadDataIfNeeded() = _dataState.run {
        if (value !is DataState.Loading && value.data == null) {
            value = DataState.Loading(value.data)
            value = DataState.Idle(loadDataFromLocalSource())
        }
        value.data!!
    }

    protected suspend fun saveData(data: T) = _dataState.run {
        value = DataState.Loading(data)
        saveDataToLocalSource(data)
        value = DataState.Idle(data)
    }
}