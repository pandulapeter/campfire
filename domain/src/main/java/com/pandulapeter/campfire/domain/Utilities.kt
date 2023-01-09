package com.pandulapeter.campfire.domain

import com.pandulapeter.campfire.data.model.Result

internal inline fun <T> resultOf(action: () -> T) = try {
    Result.Success(action())
} catch (exception: Exception) {
    Result.Failure(exception)
}