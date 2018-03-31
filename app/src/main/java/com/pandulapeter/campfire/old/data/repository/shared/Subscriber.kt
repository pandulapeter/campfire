package com.pandulapeter.campfire.old.data.repository.shared

/**
 * Implemented by classes who want to observe changes in a repository.
 */
interface Subscriber {

    fun onUpdate(updateType: UpdateType)
}