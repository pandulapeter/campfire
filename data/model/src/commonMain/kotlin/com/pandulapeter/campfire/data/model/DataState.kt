package com.pandulapeter.campfire.data.model

sealed class DataState<T> {

    abstract val data: T?

    data class Failure<T>(override val data: T?) : DataState<T>()

    data class Idle<T>(override val data: T) : DataState<T>()

    data class Loading<T>(override val data: T?) : DataState<T>()
}