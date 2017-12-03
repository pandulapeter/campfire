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
        subscriber.onUpdate(UpdateType.InitialUpdate(this.javaClass))
    }

    fun unsubscribe(subscriber: Subscriber) {
        if (subscribers.contains(subscriber)) {
            subscribers.remove(subscriber)
        }
    }

    protected fun notifySubscribers(updateType: UpdateType = UpdateType.Unspecified) = subscribers.forEach { it.onUpdate(updateType) }

    /**
     * Implemented by classes who want to observe changes in a repository.
     */
    interface Subscriber {

        fun onUpdate(updateType: UpdateType)
    }

    /**
     * Represents all the possible update events.
     *
     * TODO: Add more events to avoid unnecessary updates.
     */
    sealed class UpdateType {
        // General
        object Unspecified : UpdateType()

        class InitialUpdate(from : Class<Repository>) : UpdateType()

        // Language

        // Playlist

        // SongInfo

        // UserPreference
    }
}