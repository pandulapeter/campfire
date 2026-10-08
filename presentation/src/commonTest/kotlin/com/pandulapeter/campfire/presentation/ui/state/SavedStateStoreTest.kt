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

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SavedStateStoreTest {

    @Test
    fun `a value persisted is restored`() = runTest {
        val store = SavedStateStore(SavedStateHandle(), backgroundScope)
        store.persist(SavedStateStore.SONG_PICKER_TAGS_KEY, listOf("a", "b"))
        assertEquals(listOf("a", "b"), store.restore<List<String>>(SavedStateStore.SONG_PICKER_TAGS_KEY))
    }

    @Test
    fun `nothing saved and something unreadable restore nothing`() = runTest {
        val handle = SavedStateHandle()
        val store = SavedStateStore(handle, backgroundScope)
        assertNull(store.restore<List<String>>(SavedStateStore.SONG_PICKER_TAGS_KEY))
        store.persistJson(SavedStateStore.SONG_PICKER_TAGS_KEY, "{not json")
        assertNull(store.restore<List<String>>(SavedStateStore.SONG_PICKER_TAGS_KEY))
    }

    @Test
    fun `a search is restored open with its query`() = runTest {
        val store = SavedStateStore(SavedStateHandle(), backgroundScope)
        store.persistJson(SavedStateStore.SONGS_SEARCH_KEY, """{"isOpen":true,"query":"fire"}""")
        val search = store.restoreSearch(SavedStateStore.SONGS_SEARCH_KEY)
        assertEquals(true, search.isOpen.value)
        assertEquals("fire", search.textFieldState.text.toString())
    }
}
