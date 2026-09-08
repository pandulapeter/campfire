package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.repository.api.RawSongDetailsRepository
import com.pandulapeter.campfire.data.source.local.api.RawSongDetailsLocalSource
import com.pandulapeter.campfire.data.source.remote.api.RawSongDetailsRemoteSource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class RawSongDetailsRepositoryImpl(
    private val rawSongDetailsLocalSource: RawSongDetailsLocalSource,
    private val rawSongDetailsRemoteSource: RawSongDetailsRemoteSource
) : RawSongDetailsRepository {

    // Loading rather than Failure, so that "nothing has been read yet" is not reported to the UI as an error.
    private val _downloadedSongUrls = MutableStateFlow<DataState<Set<String>>>(DataState.Loading(null))
    override val downloadedSongUrls = _downloadedSongUrls
    private val downloadedSongUrlsMutex = Mutex()

    // Starts out empty rather than unknown: the songs are read one by one, so there is never a point where all of
    // them are still missing.
    private val _rawSongDetails = MutableStateFlow<DataState<Map<String, RawSongDetails>>>(DataState.Idle(emptyMap()))
    override val rawSongDetails = _rawSongDetails

    override suspend fun loadDownloadedSongUrlsIfNeeded(): Boolean = downloadedSongUrlsMutex.withLock {
        _downloadedSongUrls.run {
            if (value.data != null) return@run true
            value = DataState.Loading(null)
            try {
                value = DataState.Idle(rawSongDetailsLocalSource.loadDownloadedSongUrls())
                true
            } catch (exception: Exception) {
                println(exception.message)
                value = DataState.Failure(null)
                false
            }
        }
    }

    override suspend fun loadRawSongDetails(url: String, isForceRefresh: Boolean): Boolean {
        val cached = _rawSongDetails.value.data?.get(url)
            // The saved text first, so an already downloaded song is readable before (and without) the network.
            ?: loadFromLocalSource(url)?.also { publish(it) }
        if (!isForceRefresh && cached != null && !cached.isStale()) return true
        // A song that is already on screen is refreshed without a loading state: the text stays as it is until the
        // new one has arrived, and nothing about the update is visible unless the song has actually changed.
        if (cached == null || isForceRefresh) {
            _rawSongDetails.value = DataState.Loading(_rawSongDetails.value.data)
        }
        return try {
            val rawSongDetails = RawSongDetails(
                url = url,
                rawData = rawSongDetailsRemoteSource.loadRawSongDetails(url),
                refreshTimestamp = now()
            )
            publish(rawSongDetails)
            saveToLocalSource(rawSongDetails)
            _downloadedSongUrls.value = DataState.Idle(_downloadedSongUrls.value.data.orEmpty() + url)
            true
        } catch (exception: Exception) {
            println(exception.message)
            val currentData = _rawSongDetails.value.data
            // A failed refresh of a song that is already saved is not a failure the user needs to see.
            val dataWithSavedCopy = currentData?.takeIf { !isForceRefresh && it.containsKey(url) }
            _rawSongDetails.value = if (dataWithSavedCopy == null) DataState.Failure(currentData) else DataState.Idle(dataWithSavedCopy)
            dataWithSavedCopy != null
        }
    }

    /** Storage that cannot be read is the same as storage that holds nothing: the remote source is asked next. */
    private suspend fun loadFromLocalSource(url: String) = try {
        rawSongDetailsLocalSource.loadRawSongDetails(url)
    } catch (exception: Exception) {
        println(exception.message)
        null
    }

    /** The text that arrived is readable whether or not it could be saved for later, so this failure is not reported. */
    private suspend fun saveToLocalSource(rawSongDetails: RawSongDetails) = try {
        rawSongDetailsLocalSource.saveRawSongDetails(rawSongDetails)
    } catch (exception: Exception) {
        println(exception.message)
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
