/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.messages

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The snackbar's queue, and the one way the library's writes are launched, which turns a failed one into a message. */
internal class MessageSink(
    private val scope: CoroutineScope,
) {

    /**
     * Messages waiting for the snackbar, oldest first, each with a number of its own so that two identical results in
     * a row are still two messages. Held here rather than by the screen that shows them: Android recreates that screen
     * whenever it recreates its activity, and a message it had already taken would go with it unshown. A message
     * leaves the queue once it has been shown, see [onMessageShown].
     */
    private val _messageQueue = MutableStateFlow(emptyList<IndexedValue<Message>>())
    val messageQueue: StateFlow<List<IndexedValue<Message>>> = _messageQueue.asStateFlow()
    private var messageCount = 0

    fun sendMessage(message: Message) {
        val indexedMessage = IndexedValue(messageCount++, message)
        _messageQueue.update { it + indexedMessage }
    }

    /** Called by the snackbar host once [message] has been on screen for its whole duration, or dismissed. */
    fun onMessageShown(message: IndexedValue<Message>) = _messageQueue.update { queue -> queue.filterNot { it.index == message.index } }

    /**
     * [scope]'s `launch` for the intents that write to the library. A write that fails throws out of the
     * repository, and an exception nobody catches in a launched coroutine takes the whole app down on Android: here
     * it becomes one line at the bottom of the screen instead, and the library stays what it was.
     */
    fun launchLibraryChange(block: suspend () -> Unit) = scope.launch {
        try {
            block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("The change could not be written: ${exception.message}")
            sendMessage(Message.OperationFailed)
        }
    }
}
