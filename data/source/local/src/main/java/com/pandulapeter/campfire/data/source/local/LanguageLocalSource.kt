package com.pandulapeter.campfire.data.source.local

import com.pandulapeter.campfire.data.model.domain.Language

interface LanguageLocalSource {

    suspend fun getLanguages(): List<Language>

    suspend fun saveLanguages(songs: List<Language>)
}