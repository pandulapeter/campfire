package com.pandulapeter.campfire.data.model

sealed class Result<T> {

    data class Success<T>(val data: T) : Result<T>()

    data class Failure<T>(val exception: Exception) : Result<T>()
}