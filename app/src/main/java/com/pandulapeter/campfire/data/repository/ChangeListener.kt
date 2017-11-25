package com.pandulapeter.campfire.data.repository

/**
 * Used to communicate the results of repository operations.
 */
data class ChangeListener<T>(val onNext: (T) -> Unit,
                             val onComplete: () -> Unit,
                             val onError: () -> Unit)