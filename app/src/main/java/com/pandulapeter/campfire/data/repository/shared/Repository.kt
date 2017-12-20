package com.pandulapeter.campfire.data.repository.shared

/**
 * Base class for all repositories that handles the subscription management.
 */
abstract class Repository {
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
}