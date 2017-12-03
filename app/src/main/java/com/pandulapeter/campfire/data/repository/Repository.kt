package com.pandulapeter.campfire.data.repository

/**
 * Base class for all repositories.
 */
abstract class Repository {
    private var subscribers = mutableSetOf<Subscriber>()

    fun subscribe(subscriber: Subscriber) {
        if (!subscribers.contains(subscriber)) {
            subscribers.add(subscriber)
        }
        subscriber.onUpdate()
    }

    fun unsubscribe(subscriber: Subscriber) {
        if (subscribers.contains(subscriber)) {
            subscribers.remove(subscriber)
        }
    }

    protected fun notifySubscribers() = subscribers.forEach { it.onUpdate() }

    /**
     * Implemented by classes who want to observe changes in a repository.
     */
    interface Subscriber {

        //TODO: Add a parameter to specify the type of the update, right now too many unnecessary updates are triggered.
        fun onUpdate()
    }
}