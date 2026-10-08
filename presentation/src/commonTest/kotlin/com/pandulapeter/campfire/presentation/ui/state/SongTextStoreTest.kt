/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SongTextStoreTest {

    @Test
    fun `an edit that changes nothing writes nothing`() = runTest {
        val files = FakeSongFiles(mapOf(FILE to "{title: A}"))
        val store = files.songTextStore(backgroundScope, MessageSink(backgroundScope))
        store.editSongText(FILE) { it }
        assertEquals(emptyList(), files.writes)
    }

    @Test
    fun `an edit is written and the held text follows it`() = runTest {
        val files = FakeSongFiles(mapOf(FILE to "{title: A}"))
        val store = files.songTextStore(backgroundScope, MessageSink(backgroundScope))
        store.editSongText(FILE) { "$it\n{tag: rock}" }
        assertEquals("{title: A}\n{tag: rock}", files.files[FILE])
        assertEquals("{title: A}\n{tag: rock}", store.songTexts.value[FILE])
    }

    @Test
    fun `an edit built on a held text the file has moved on from is applied to the file's text instead`() = runTest {
        val files = FakeSongFiles(mapOf(FILE to "{title: Synced}"))
        val sink = MessageSink(backgroundScope)
        val store = files.songTextStore(backgroundScope, sink)
        store.updateSongTexts { mapOf(FILE to "{title: Old}") }

        store.editSongText(FILE) { "$it\n{tag: rock}" }

        assertEquals("{title: Synced}\n{tag: rock}", files.files[FILE])
        assertEquals("{title: Synced}\n{tag: rock}", store.songTexts.value[FILE])
        assertEquals(emptyList(), sink.messageQueue.value)
    }

    @Test
    fun `a file that changes under both attempts is reported and the held text is left alone`() = runTest {
        val files = FakeSongFiles(mapOf(FILE to "{title: A}"), changeBeforeEveryWrite = { "$it!" })
        val sink = MessageSink(backgroundScope)
        val store = files.songTextStore(backgroundScope, sink)
        store.updateSongTexts { mapOf(FILE to "{title: A}") }

        store.editSongText(FILE) { "$it\n{tag: rock}" }

        assertEquals(emptyList(), files.writes)
        assertEquals("{title: A}", store.songTexts.value[FILE])
        assertEquals(listOf<Message>(Message.OperationFailed), sink.messageQueue.value.map { it.value })
    }

    @Test
    fun `an edit of a file that is gone does nothing and says nothing`() = runTest {
        val files = FakeSongFiles()
        val sink = MessageSink(backgroundScope)
        val store = files.songTextStore(backgroundScope, sink)

        store.editSongText(FILE) { "$it\n{tag: rock}" }

        assertEquals(emptyList(), files.writes)
        assertNull(store.songTexts.value[FILE])
        assertEquals(emptyList(), sink.messageQueue.value)
    }

    private companion object {
        const val FILE = "song.cho"
    }
}
