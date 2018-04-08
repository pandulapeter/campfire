package com.pandulapeter.campfire.old.data.repository

import com.pandulapeter.campfire.data.model.local.Language
import com.pandulapeter.campfire.old.data.model.SongInfo
import com.pandulapeter.campfire.old.data.repository.shared.Repository
import com.pandulapeter.campfire.old.data.repository.shared.Subscriber
import com.pandulapeter.campfire.old.data.repository.shared.UpdateType
import com.pandulapeter.campfire.old.data.storage.PreferenceStorageManager
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async

/**
 * Wraps caching and updating of [Language] objects.
 */
class LanguageRepository(private val preferenceStorageManager: PreferenceStorageManager) : Repository() {
    private var dataSet: Map<Language, Boolean> = HashMap()

    override fun subscribe(subscriber: Subscriber) {
        super.subscribe(subscriber)
        subscriber.onUpdate(UpdateType.LanguagesUpdated(dataSet))
    }

    fun updateLanguages(songInfoList: List<SongInfo>) {
        async(UI) {
            async(CommonPool) {
                val languageFilters = HashMap<Language, Boolean>()
                languageFilters
            }.await().let {
                dataSet = it
                notifySubscribers(UpdateType.LanguagesUpdated(it))
            }
        }
    }

    fun isLanguageFilterEnabled(language: Language) = dataSet[language] ?: preferenceStorageManager.isLanguageFilterEnabled(language)

    fun setLanguageFilterEnabled(language: Language, isEnabled: Boolean) {
        if (isLanguageFilterEnabled(language) != isEnabled) {
            dataSet = dataSet.toMutableMap().apply { set(language, isEnabled) }
            preferenceStorageManager.setLanguageFilterEnabled(language, isEnabled)
            notifySubscribers(UpdateType.LanguageFilterChanged)
        }
    }
}