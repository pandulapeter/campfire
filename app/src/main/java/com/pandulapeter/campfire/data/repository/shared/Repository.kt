package com.pandulapeter.campfire.data.repository.shared

abstract class Repository<out T> {

    private var subscribers = mutableSetOf<Subscriber<T>>()
    protected abstract val data: T

    fun subscribe(subscriber: Subscriber<T>) {
        if (!subscribers.contains(subscriber)) {
            subscribers.add(subscriber)
        }
        subscriber.onLoadingStateChanged()
        subscriber.onDataChanged(data)
    }

    fun unsubscribe(subscriber: Subscriber<T>) {
        if (subscribers.contains(subscriber)) {
            subscribers.remove(subscriber)
        }
    }

    protected fun notifyDataChanged() = subscribers.forEach { it.onDataChanged(data) }

    protected fun notifyLoadingStateChanged() = subscribers.forEach { it.onLoadingStateChanged() }

    protected fun notifyError() = subscribers.forEach { it.onError() }
}