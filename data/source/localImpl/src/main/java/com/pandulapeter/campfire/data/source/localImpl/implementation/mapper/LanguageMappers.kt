package com.pandulapeter.campfire.data.source.localImpl.implementation.mapper

import com.pandulapeter.campfire.data.model.domain.Language
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.LanguageEntity

internal fun LanguageEntity.toModel() = Language(
    id = id,
    nameEn = nameEn,
    nameHu = nameHu,
    nameRo = nameRo
)

internal fun Language.toEntity() = LanguageEntity(
    id = id,
    nameEn = nameEn,
    nameHu = nameHu,
    nameRo = nameRo
)