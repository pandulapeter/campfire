package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.Language
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.storage.StorageManager
import com.pandulapeter.campfire.util.mapToLanguage

/**
 * Wraps caching and updating of [Language] objects.
 */
class LanguageRepository(private val storageManager: StorageManager) : Repository() {
    private val languageFilters = HashMap<Language, Boolean>()

    fun updateLanguages(songInfoList: List<SongInfo>) {
        languageFilters.clear()
        songInfoList.map { it.language.mapToLanguage() }.distinct().forEach {
            languageFilters.put(it, isLanguageFilterEnabled(it))
        }
    }

    fun getLanguages() = languageFilters.keys.toList()

    fun isLanguageFilterEnabled(language: Language) = if (languageFilters.containsKey(language)) {
        languageFilters[language] == true
    } else {
        storageManager.isLanguageFilterEnabled(language)
    }

    fun setLanguageFilterEnabled(language: Language, isEnabled: Boolean) {
        if (isLanguageFilterEnabled(language) != isEnabled) {
            languageFilters[language] = isEnabled
            storageManager.setLanguageFilterEnabled(language, isEnabled)
            notifySubscribers()
        }
    }
}