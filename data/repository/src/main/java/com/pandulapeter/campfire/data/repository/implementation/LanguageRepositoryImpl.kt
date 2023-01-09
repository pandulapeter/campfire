package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.Language
import com.pandulapeter.campfire.data.repository.api.LanguageRepository
import com.pandulapeter.campfire.data.source.local.LanguageLocalSource
import com.pandulapeter.campfire.data.source.remote.api.LanguageRemoteSource

internal class LanguageRepositoryImpl(
    languageLocalSource: LanguageLocalSource,
    languageRemoteSource: LanguageRemoteSource
) : BaseRepository<List<Language>>(
    getDataFromLocalSource = languageLocalSource::getLanguages,
    getDataFromRemoteSource = languageRemoteSource::getLanguages,
    saveDataToLocalSource = languageLocalSource::saveLanguages,
), LanguageRepository {

    override fun isDataValid(data: List<Language>) = data.isNotEmpty()

    override fun areLanguagesAvailable() = isDataAvailable()

    override suspend fun getLanguages(isForceRefresh: Boolean) = getData(
        isForceRefresh = isForceRefresh
    )
}