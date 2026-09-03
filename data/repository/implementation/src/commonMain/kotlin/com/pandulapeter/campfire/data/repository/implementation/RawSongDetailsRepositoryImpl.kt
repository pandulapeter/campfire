package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.repository.api.RawSongDetailsRepository
import com.pandulapeter.campfire.data.source.local.api.RawSongDetailsLocalSource
import com.pandulapeter.campfire.data.source.remote.api.RawSongDetailsRemoteSource
import kotlinx.coroutines.flow.MutableStateFlow

internal class RawSongDetailsRepositoryImpl(
    private val rawSongDetailsLocalSource: RawSongDetailsLocalSource,
    private val rawSongDetailsRemoteSource: RawSongDetailsRemoteSource
) : RawSongDetailsRepository {

    private val isFirstLoadingDone = mutableListOf<String>()
    private val _rawSongDetails = MutableStateFlow<DataState<Map<String, RawSongDetails>>>(DataState.Failure(null))
    override val rawSongDetails = _rawSongDetails

    override suspend fun loadRawSongDetailsIfNeeded() = _rawSongDetails.run {
        if (value !is DataState.Loading && value.data == null) {
            value = DataState.Loading(value.data)
            value = DataState.Idle(rawSongDetailsLocalSource.loadRawSongDetails().associateBy { it.url })
        }
    }

    override suspend fun loadRawSongDetails(url: String, isForceRefresh: Boolean) {
        _rawSongDetails.value.data?.get(url).let { rawSongData ->
            if (rawSongData == null || isForceRefresh || !isFirstLoadingDone.contains(url)) {
                _rawSongDetails.value = DataState.Loading(_rawSongDetails.value.data)
                try {
                    val rawSongDetailsData = rawSongDetailsRemoteSource.loadRawSongDetails(url)
                    val rawSongDetails = RawSongDetails(
                        url = url,
                        rawData = rawSongDetailsData
                    )
                    val newMap = (_rawSongDetails.value.data ?: mapOf()).toMutableMap().apply {
                        this[url] = rawSongDetails
                    }
                    _rawSongDetails.value = DataState.Loading(newMap)
                    rawSongDetailsLocalSource.saveRawSongDetails(rawSongDetails)
                    isFirstLoadingDone.add(url)
                    _rawSongDetails.value = DataState.Idle(newMap)
                } catch (exception: Exception) {
                    println(exception.message)
                    val currentData = _rawSongDetails.value.data
                    _rawSongDetails.value = if (currentData == null || isForceRefresh) {
                        DataState.Failure(currentData)
                    } else {
                        DataState.Idle(currentData)
                    }
                }
            }
        }
    }
}