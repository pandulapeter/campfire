package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.local.Language
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.networking.NetworkManager
import com.pandulapeter.campfire.data.persistence.Database
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.shared.Repository
import com.pandulapeter.campfire.util.enqueueCall
import com.pandulapeter.campfire.util.swap
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async

class CollectionRepository(
    private val preferenceDatabase: PreferenceDatabase,
    private val networkManager: NetworkManager,
    private val database: Database
) : Repository<CollectionRepository.Subscriber>() {

    companion object {
        private const val UPDATE_LIMIT = 24 * 60 * 60 * 1000
    }

    private val data = mutableListOf<Collection>()
    val languages = mutableListOf<Language>()
    private var isCacheLoaded = false
    private var isLoading = true
        set(value) {
            if (field != value) {
                field = value
                subscribers.forEach { it.onCollectionsLoadingStateChanged(value) }
            }
        }

    init {
        async(UI) {
            async(CommonPool) {
                data.swap(database.collectionDao().getAll())
                updateLanguages()
            }.await()
            if (System.currentTimeMillis() - preferenceDatabase.lastCollectionsUpdateTimestamp > UPDATE_LIMIT) {
                updateData()
            } else {
                isLoading = false
            }
            isCacheLoaded = true
            if (data.isNotEmpty()) {
                notifyDataChanged()
            }
        }
    }

    override fun subscribe(subscriber: Subscriber) {
        super.subscribe(subscriber)
        if (!isLoading) {
            subscriber.onCollectionsUpdated(data)
        }
        subscriber.onCollectionsLoadingStateChanged(isLoading)
        if (isCacheLoaded && !isLoading && data.isEmpty()) {
            subscriber.onCollectionRepositoryUpdateError()
        }
    }

    fun isCacheLoaded() = isCacheLoaded

    fun updateData() {
        isLoading = true
        networkManager.service.getCollections().enqueueCall(
            onSuccess = { newData ->
                async(UI) {
                    async(CommonPool) {
                        if (data.isNotEmpty()) {
                            newData.forEach { song ->
                                val oldSong = data.find { it.id == song.id }
                                if (oldSong == null) {
                                    song.isNew = true
                                } else {
                                    song.isBookmarked = oldSong.isBookmarked
                                }
                            }
                        }
                        data.swap(newData)
                        updateLanguages()
                    }.await()
                    async(CommonPool) { database.collectionDao().updateAll(data) }
                    isLoading = false
                    notifyDataChanged()
                    preferenceDatabase.lastCollectionsUpdateTimestamp = System.currentTimeMillis()

                }
            },
            onFailure = {
                isLoading = false
                subscribers.forEach { it.onCollectionRepositoryUpdateError() }
            })
    }

    fun onCollectionOpened(collectionId: String) {
        data.find { it.id == collectionId }?.let {
            if (it.isNew) {
                it.isNew = false
                notifyDataChanged()
            }
        }
    }

    fun toggleSavedState(collectionId: String) {
        data.find { it.id == collectionId }?.let {
            it.isBookmarked = !(it.isBookmarked ?: false)
            async(CommonPool) { database.collectionDao().insert(it) }
        }
    }

    private fun updateLanguages() {
        languages.clear()
        data.forEach {
            it.language?.forEach {
                languages.add(
                    when (it) {
                        Language.SupportedLanguages.ENGLISH.id -> Language.Known.English
                        Language.SupportedLanguages.HUNGARIAN.id -> Language.Known.Hungarian
                        Language.SupportedLanguages.ROMANIAN.id -> Language.Known.Romanian
                        else -> Language.Unknown
                    }
                )
            }
        }
        languages.swap(languages.distinct().sortedBy { it.nameResource })
    }

    private fun notifyDataChanged() = subscribers.forEach { it.onCollectionsUpdated(data) }

    interface Subscriber {

        fun onCollectionsUpdated(data: List<Collection>)

        fun onCollectionsLoadingStateChanged(isLoading: Boolean)

        fun onCollectionRepositoryUpdateError()
    }
}