package com.pandulapeter.campfire.data.repository.shared

abstract class Repository<T> {

    private var subscribers = mutableSetOf<Subscriber<T>>()
    protected abstract val data: T

    fun subscribe(subscriber: Subscriber<T>) {
        if (!subscribers.contains(subscriber)) {
            subscribers.add(subscriber)
        }
        subscriber.onUpdate(data)
    }

    fun unsubscribe(subscriber: Subscriber<T>) {
        if (subscribers.contains(subscriber)) {
            subscribers.remove(subscriber)
        }
    }

    protected fun notifySubscribers() = subscribers.forEach { it.onUpdate(data) }
}