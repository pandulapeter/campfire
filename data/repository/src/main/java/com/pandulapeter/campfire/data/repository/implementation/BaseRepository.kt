package com.pandulapeter.campfire.data.repository.implementation

internal abstract class BaseRepository<T>(
    private val getDataFromLocalSource: suspend () -> T,
    private val getDataFromRemoteSource: suspend () -> T,
    private val saveDataToLocalSource: suspend (T) -> Unit
) {
    private var cache: T? = null

    protected fun isDataAvailable() = cache != null

    abstract fun isDataValid(data: T): Boolean

    protected suspend fun getData(isForceRefresh: Boolean) = cache.let { currentCache ->
        if (isForceRefresh || currentCache == null) {
            if (isForceRefresh) {
                loadRemoteData()
            } else {
                loadLocalData().let { localData ->
                    if (isDataValid(localData)) {
                        localData
                    } else {
                        loadRemoteData()
                    }
                }
            }
        } else {
            currentCache
        }
    }

    private suspend fun loadRemoteData() = getDataFromRemoteSource().also {
        saveDataToLocalSource(it)
        cache = it
    }

    private suspend fun loadLocalData() = getDataFromLocalSource().also {
        if (isDataValid(it)) {
            cache = it
        }
    }
}