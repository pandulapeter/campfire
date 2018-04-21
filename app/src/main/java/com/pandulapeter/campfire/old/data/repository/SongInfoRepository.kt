package com.pandulapeter.campfire.old.data.repository

import com.pandulapeter.campfire.old.data.model.SongInfo
import com.pandulapeter.campfire.old.data.repository.shared.Repository
import com.pandulapeter.campfire.old.data.repository.shared.Subscriber
import com.pandulapeter.campfire.old.data.repository.shared.UpdateType
import com.pandulapeter.campfire.old.data.storage.DataStorageManager
import com.pandulapeter.campfire.old.data.storage.PreferenceStorageManager
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * Wraps caching and updating of [SongInfo] objects.
 */
class SongInfoRepository(
    preferenceStorageManager: PreferenceStorageManager,
    private val dataStorageManager: DataStorageManager
) : Repository() {
    private var dataSet by Delegates.observable(dataStorageManager.songInfoCache) { _, _, new ->
        dataStorageManager.songInfoCache = new
    }
    private var isLoading by Delegates.observable(false) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) notifySubscribers(UpdateType.LoadingStateChanged())
    }

    init {
        if (!isLoading && System.currentTimeMillis() - preferenceStorageManager.lastUpdateTimestamp > CACHE_VALIDITY_LIMIT) {
            updateDataSet()
        }
    }

    override fun subscribe(subscriber: Subscriber) {
        super.subscribe(subscriber)
        subscriber.onUpdate(UpdateType.LibraryCacheUpdated())
        subscriber.onUpdate(UpdateType.LoadingStateChanged())
    }

    fun getLibrarySongs(): List<SongInfo> = dataSet.values.toList()

    fun getSongInfo(id: String) = dataSet[id]

    private fun updateDataSet() {
        isLoading = true
    }

    private companion object {
        private const val CACHE_VALIDITY_LIMIT = 1000 * 60 * 60 * 24
    }
}