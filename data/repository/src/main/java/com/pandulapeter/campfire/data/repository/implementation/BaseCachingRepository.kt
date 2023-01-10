package com.pandulapeter.campfire.data.repository.implementation

internal abstract class BaseCachingRepository<T>(
    private val getDataFromLocalSource: suspend (sheetUrl: String) -> T,
    private val getDataFromRemoteSource: suspend (sheetUrl: String) -> T,
    private val saveDataToLocalSource: suspend (sheetUrl: String, T) -> Unit
) {
    private var cache: T? = null

    abstract fun isDataValid(data: T): Boolean

    protected suspend fun getData(sheetUrl: String, isForceRefresh: Boolean) = cache.let { currentCache ->
        if (isForceRefresh || currentCache == null) {
            if (isForceRefresh) {
                loadRemoteData(sheetUrl)
            } else {
                loadLocalData(sheetUrl).let { localData ->
                    if (isDataValid(localData)) {
                        localData
                    } else {
                        loadRemoteData(sheetUrl)
                    }
                }
            }
        } else {
            currentCache
        }
    }

    private suspend fun loadRemoteData(sheetUrl: String) = getDataFromRemoteSource(sheetUrl).also {
        saveDataToLocalSource(sheetUrl, it)
        cache = it
    }

    private suspend fun loadLocalData(sheetUrl: String) = getDataFromLocalSource(sheetUrl).also {
        if (isDataValid(it)) {
            cache = it
        }
    }
}