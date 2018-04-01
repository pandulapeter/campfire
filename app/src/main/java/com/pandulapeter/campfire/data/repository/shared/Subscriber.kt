package com.pandulapeter.campfire.data.repository.shared

interface Subscriber<in T> {

    fun onDataChanged(data: T)

    fun onLoadingStateChanged()

    fun onError()
}