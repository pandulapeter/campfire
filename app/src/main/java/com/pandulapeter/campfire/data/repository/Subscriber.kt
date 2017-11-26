package com.pandulapeter.campfire.data.repository

/**
 * Implemented by classes who want to observer a repository.
 */
interface Subscriber {

    //TODO: Add a parameter to specify the type of the update, right now too many unnecessary updates are triggered.
    fun onUpdate()
}