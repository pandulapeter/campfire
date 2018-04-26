package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.local.Language
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.networking.NetworkManager
import com.pandulapeter.campfire.data.persistence.Database
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.shared.Repository
import com.pandulapeter.campfire.util.enqueueCall
import com.pandulapeter.campfire.util.swap
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async

class SongRepository(
    private val preferenceDatabase: PreferenceDatabase,
    private val networkManager: NetworkManager,
    private val database: Database
) : Repository<SongRepository.Subscriber>() {

    companion object {
        private const val UPDATE_LIMIT = 24 * 60 * 60 * 1000
    }

    private val data = mutableListOf<Song>()
    val languages = mutableListOf<Language>()
    private var isCacheLoaded = false
    private var isLoading = true
        set(value) {
            if (field != value) {
                field = value
                subscribers.forEach { it.onSongRepositoryLoadingStateChanged(value) }
            }
        }

    init {
        async(UI) {
            async(CommonPool) {
                data.swap(database.songDao().getAll())
                updateLanguages()
            }.await()
            if (System.currentTimeMillis() - preferenceDatabase.lastLibraryUpdateTimestamp > UPDATE_LIMIT) {
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
            subscriber.onSongRepositoryDataUpdated(data)
        }
        subscriber.onSongRepositoryLoadingStateChanged(isLoading)
        if (!isLoading && data.isEmpty()) {
            subscriber.onSongRepositoryUpdateError()
        }
    }

    fun isCacheLoaded() = isCacheLoaded

    fun updateData() {
        isLoading = true
        networkManager.service.getLibrary().enqueueCall(
            onSuccess = { newData ->
                async(UI) {
                    async(CommonPool) {
                        if (data.isNotEmpty()) {
                            newData.forEach { song ->
                                if (data.find { it.id == song.id } == null) {
                                    song.isNew = true
                                }
                            }
                        }
                        data.swap(newData)
                        updateLanguages()
                    }.await()
                    async(CommonPool) { database.songDao().updateAll(data) }
                    isLoading = false
                    notifyDataChanged()
                    preferenceDatabase.lastLibraryUpdateTimestamp = System.currentTimeMillis()

                }
            },
            onFailure = {
                isLoading = false
                subscribers.forEach { it.onSongRepositoryUpdateError() }
            })
    }

    fun onSongOpened(songId: String) {
        data.find { it.id == songId }?.let {
            if (it.isNew) {
                it.isNew = false
                notifyDataChanged()
            }
        }
    }

    private fun updateLanguages() {
        languages.swap(data
            .map {
                when (it.language) {
                    Language.SupportedLanguages.ENGLISH.id -> Language.Known.English
                    Language.SupportedLanguages.HUNGARIAN.id -> Language.Known.Hungarian
                    Language.SupportedLanguages.ROMANIAN.id -> Language.Known.Romanian
                    else -> Language.Unknown
                }
            }
            .distinct()
            .sortedBy { it.nameResource }
        )
    }

    private fun notifyDataChanged() = subscribers.forEach { it.onSongRepositoryDataUpdated(data) }

    interface Subscriber {

        fun onSongRepositoryDataUpdated(data: List<Song>)

        fun onSongRepositoryLoadingStateChanged(isLoading: Boolean)

        fun onSongRepositoryUpdateError()
    }
}