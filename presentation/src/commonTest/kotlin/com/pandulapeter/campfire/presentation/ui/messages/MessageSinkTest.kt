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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageSinkTest {

    @Test
    fun `two identical messages in a row are two messages`() = runTest {
        val sink = MessageSink(backgroundScope)
        sink.sendMessage(Message.OperationFailed)
        sink.sendMessage(Message.OperationFailed)
        assertEquals(listOf(0, 1), sink.messageQueue.value.map { it.index })
    }

    @Test
    fun `a shown message leaves the queue and only that one`() = runTest {
        val sink = MessageSink(backgroundScope)
        sink.sendMessage(Message.OperationFailed)
        sink.sendMessage(Message.ExportFailed)
        sink.onMessageShown(sink.messageQueue.value.first())
        assertEquals(listOf(IndexedValue(1, Message.ExportFailed)), sink.messageQueue.value)
    }

    @Test
    fun `a library change that fails becomes a message`() = runTest {
        val sink = MessageSink(this)
        sink.launchLibraryChange { throw IllegalStateException("The disk is full.") }.join()
        assertEquals(listOf<Message>(Message.OperationFailed), sink.messageQueue.value.map { it.value })
    }
}
