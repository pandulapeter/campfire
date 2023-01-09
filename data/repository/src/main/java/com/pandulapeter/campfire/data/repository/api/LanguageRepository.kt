package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.domain.Language

interface LanguageRepository {

    fun areLanguagesAvailable(): Boolean

    suspend fun getLanguages(isForceRefresh: Boolean): List<Language>
}