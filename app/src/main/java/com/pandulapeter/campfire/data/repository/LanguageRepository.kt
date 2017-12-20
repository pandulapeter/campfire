package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.Language
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.repository.shared.Repository
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.data.storage.PreferenceStorageManager
import com.pandulapeter.campfire.util.mapToLanguage

/**
 * Wraps caching and updating of [Language] objects.
 */
class LanguageRepository(private val preferenceStorageManager: PreferenceStorageManager) : Repository() {
    private var dataSet: Map<Language, Boolean> = HashMap()

    fun updateLanguages(songInfoList: List<SongInfo>) {
        val languageFilters = HashMap<Language, Boolean>()
        songInfoList.map { it.language.mapToLanguage() }.distinct().forEach {
            languageFilters.put(it, isLanguageFilterEnabled(it))
        }
        dataSet = languageFilters
    }

    fun getLanguages() = dataSet.keys.toList()

    fun isLanguageFilterEnabled(language: Language) = dataSet[language] ?: preferenceStorageManager.isLanguageFilterEnabled(language)

    fun setLanguageFilterEnabled(language: Language, isEnabled: Boolean) {
        if (isLanguageFilterEnabled(language) != isEnabled) {
            dataSet = dataSet.toMutableMap().apply { set(language, isEnabled) }
            preferenceStorageManager.setLanguageFilterEnabled(language, isEnabled)
            notifySubscribers(UpdateType.LanguageFilterChanged(language, isEnabled))
        }
    }
}