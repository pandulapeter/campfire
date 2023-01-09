package com.pandulapeter.campfire.data.source.localImpl.implementation

import com.pandulapeter.campfire.data.model.domain.Language
import com.pandulapeter.campfire.data.source.local.LanguageLocalSource
import com.pandulapeter.campfire.data.source.localImpl.implementation.database.dao.LanguageDao
import com.pandulapeter.campfire.data.source.localImpl.implementation.mapper.toEntity
import com.pandulapeter.campfire.data.source.localImpl.implementation.mapper.toModel

internal class LanguageLocalSourceImpl(
    private val languageDao: LanguageDao
) : LanguageLocalSource {

    override suspend fun getLanguages() = languageDao.getAll().map { it.toModel() }

    override suspend fun saveLanguages(songs: List<Language>) = languageDao.updateAll(songs.map { it.toEntity() })
}