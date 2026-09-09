package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.repository.api.RawSongDetailsRepository
import com.pandulapeter.campfire.data.source.local.api.RawSongDetailsLocalSource
import kotlinx.coroutines.flow.MutableStateFlow

internal class RawSongDetailsRepositoryImpl(
    private val rawSongDetailsLocalSource: RawSongDetailsLocalSource
) : RawSongDetailsRepository {

    // Starts out empty rather than unknown: the songs are read one by one, so there is never a point where all of
    // them are still missing.
    private val _rawSongDetails = MutableStateFlow<DataState<Map<String, RawSongDetails>>>(DataState.Idle(emptyMap()))
    override val rawSongDetails = _rawSongDetails

    override suspend fun loadRawSongDetails(url: String): Boolean {
        if (_rawSongDetails.value.data?.containsKey(url) == true) return true
        return try {
            val saved = rawSongDetailsLocalSource.loadRawSongDetails(url)
            if (saved == null) false else {
                publish(saved)
                true
            }
        } catch (exception: Exception) {
            println(exception.message)
            _rawSongDetails.value = DataState.Failure(_rawSongDetails.value.data)
            false
        }
    }

    private fun publish(rawSongDetails: RawSongDetails) {
        _rawSongDetails.value = DataState.Idle(_rawSongDetails.value.data.orEmpty() + (rawSongDetails.url to rawSongDetails))
    }
}
