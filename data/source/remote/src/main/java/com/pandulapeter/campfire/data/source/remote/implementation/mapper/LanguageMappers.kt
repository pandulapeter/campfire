package com.pandulapeter.campfire.data.source.remote.implementation.mapper

import com.pandulapeter.campfire.data.model.domain.Language
import com.pandulapeter.campfire.data.source.remote.implementation.model.LanguageResponse
import com.pandulapeter.campfire.data.source.remote.implementation.model.exception.DataValidationException

internal fun LanguageResponse.toModel() = try {
    Language(
        id = id.toLanguageId(),
        nameEn = nameEn.toLanguageNameEn(),
        nameHu = nameHu.toLanguageNameHu(),
        nameRo = nameRo.toLanguageNameRo()
    )
} catch (exception: DataValidationException) {
    println(exception.message)
    null
}

private fun String?.toLanguageId() = toId("Missing language ID.")

private fun String?.toLanguageNameEn() = toText("Missing language English name.")

private fun String?.toLanguageNameHu() = toText("Missing language Hungarian name.")

private fun String?.toLanguageNameRo() = toText("Missing language Romanian name.")