package com.pandulapeter.campfire.data.source.remote.implementation.mapper

import com.pandulapeter.campfire.data.source.remote.implementation.model.exception.DataValidationException

internal fun Boolean?.toBoolean() = this == true

internal fun String?.toId(errorMessage: String) = if (isNullOrBlank()) throw DataValidationException(errorMessage) else
    replace(" ", "")
        .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        .trim()

internal fun String?.toText(errorMessage: String) = if (isNullOrBlank()) throw DataValidationException(errorMessage) else trim()

internal fun String?.toUrl(errorMessage: String) = if (isNullOrBlank()) throw DataValidationException(errorMessage) else trim()