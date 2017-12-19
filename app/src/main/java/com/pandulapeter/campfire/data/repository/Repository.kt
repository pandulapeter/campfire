package com.pandulapeter.campfire.data.repository

import kotlin.reflect.KClass

/**
 * Base class for all repositories.
 */
abstract class Repository<T> {
    protected abstract var dataSet: T
    private var subscribers = mutableSetOf<Subscriber>()

    fun subscribe(subscriber: Subscriber) {
        if (!subscribers.contains(subscriber)) {
            subscribers.add(subscriber)
        }
        subscriber.onUpdate(UpdateType.InitialUpdate(this::class))
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

        class InitialUpdate(val repositoryClass: KClass<out Repository<*>>) : UpdateType()

        // Language

        // Playlist
        object PlaylistAddedOrRemoved : UpdateType()

        // SongInfo

        // UserPreference
    }
}