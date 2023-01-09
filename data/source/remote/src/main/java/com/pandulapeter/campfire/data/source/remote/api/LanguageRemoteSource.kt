package com.pandulapeter.campfire.data.source.remote.api

import com.pandulapeter.campfire.data.model.domain.Language

interface LanguageRemoteSource {

    suspend fun getLanguages(): List<Language>
}