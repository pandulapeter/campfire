package com.pandulapeter.campfire.data.repository.shared

interface Subscriber<in T> {

    fun onUpdate(data: T)
}