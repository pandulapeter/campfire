package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.repository.api.RawSongDetailsRepository
import com.pandulapeter.campfire.data.source.local.api.RawSongDetailsLocalSource
import com.pandulapeter.campfire.data.source.remote.api.RawSongDetailsRemoteSource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.MutableStateFlow

internal class RawSongDetailsRepositoryImpl(
    private val rawSongDetailsLocalSource: RawSongDetailsLocalSource,
    private val rawSongDetailsRemoteSource: RawSongDetailsRemoteSource
) : RawSongDetailsRepository {

    private val _downloadedSongUrls = MutableStateFlow<DataState<Set<String>>>(DataState.Failure(null))
    override val downloadedSongUrls = _downloadedSongUrls

    // Starts out empty rather than unknown: the songs are read one by one, so there is never a point where all of
    // them are still missing.
    private val _rawSongDetails = MutableStateFlow<DataState<Map<String, RawSongDetails>>>(DataState.Idle(emptyMap()))
    override val rawSongDetails = _rawSongDetails

    override suspend fun loadDownloadedSongUrlsIfNeeded() = _downloadedSongUrls.run {
        if (value !is DataState.Loading && value.data == null) {
            value = DataState.Loading(value.data)
            value = DataState.Idle(rawSongDetailsLocalSource.loadDownloadedSongUrls())
        }
    }

    override suspend fun loadRawSongDetails(url: String, isForceRefresh: Boolean) {
        val cached = _rawSongDetails.value.data?.get(url)
            // The saved text first, so an already downloaded song is readable before (and without) the network.
            ?: rawSongDetailsLocalSource.loadRawSongDetails(url)?.also { publish(it) }
        if (!isForceRefresh && cached != null && !cached.isStale()) return
        // A song that is already on screen is refreshed without a loading state: the text stays as it is until the
        // new one has arrived, and nothing about the update is visible unless the song has actually changed.
        if (cached == null || isForceRefresh) {
            _rawSongDetails.value = DataState.Loading(_rawSongDetails.value.data)
        }
        try {
            val rawSongDetails = RawSongDetails(
                url = url,
                rawData = rawSongDetailsRemoteSource.loadRawSongDetails(url),
                refreshTimestamp = now()
            )
            publish(rawSongDetails)
            rawSongDetailsLocalSource.saveRawSongDetails(rawSongDetails)
            _downloadedSongUrls.value = DataState.Idle(_downloadedSongUrls.value.data.orEmpty() + url)
        } catch (exception: Exception) {
            println(exception.message)
            val currentData = _rawSongDetails.value.data
            // A failed refresh of a song that is already saved is not a failure the user needs to see.
            _rawSongDetails.value = if (currentData?.containsKey(url) == true && !isForceRefresh) {
                DataState.Idle(currentData)
            } else {
                DataState.Failure(currentData)
            }
        }
    }

    private fun publish(rawSongDetails: RawSongDetails) {
        _rawSongDetails.value = DataState.Idle(_rawSongDetails.value.data.orEmpty() + (rawSongDetails.url to rawSongDetails))
    }

    /**
     * A copy dated to the future is stale too: that is a clock that has been moved back, and waiting for it to catch
     * up would leave the song without updates for as long as the difference lasts.
     */
    private fun RawSongDetails.isStale() = (now() - refreshTimestamp) !in 0 until REFRESH_INTERVAL_MILLIS

    private fun now() = Clock.System.now().toEpochMilliseconds()

    private companion object {
        val REFRESH_INTERVAL_MILLIS = 1.days.inWholeMilliseconds
    }
}
