/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import kotlin.test.Test
import kotlin.test.assertEquals

/** An edit made to the whole text from a sheet or by a save leaves the caret next to the text it was next to. */
internal class EditedOffsetTest {

    @Test
    fun `a line inserted above the caret moves it by the inserted length`() =
        assertEquals("a\n{tag: x}\nve".length, editedOffset(before = "a\nverse", after = "a\n{tag: x}\nverse", offset = "a\nve".length))

    @Test
    fun `an offset before the change stays`() =
        assertEquals(1, editedOffset(before = "a\nverse", after = "a\n{tag: x}\nverse", offset = 1))

    @Test
    fun `an offset inside a respelled chord lands after the new chord name`() =
        assertEquals("[B".length, editedOffset(before = "[Bb]", after = "[B]", offset = "[Bb".length))

    @Test
    fun `a caret between two respelled lines keeps its column`() =
        assertEquals("[B]\nla".length, editedOffset(before = "[Bb]\nla la\n[Bb]", after = "[B]\nla la\n[B]", offset = "[Bb]\nla".length))

    @Test
    fun `a caret at the end of a respelled last line stays at its end`() =
        assertEquals("[B]\nla la\n[B]".length, editedOffset(before = "[Bb]\nla la\n[Bb]", after = "[B]\nla la\n[B]", offset = "[Bb]\nla la\n[Bb]".length))

    @Test
    fun `empty texts and an unchanged text map plainly`() {
        assertEquals(0, editedOffset(before = "", after = "abc", offset = 0))
        assertEquals(0, editedOffset(before = "abc", after = "", offset = 2))
        assertEquals(2, editedOffset(before = "abc", after = "abc", offset = 2))
    }

    @Test
    fun `an offset past the end is clamped`() = assertEquals(3, editedOffset(before = "abc", after = "abc", offset = 10))

    @Test
    fun `a surrogate pair at the boundary of a change is not split`() {
        val before = "a😀"
        val after = "a😁"
        assertEquals(listOf(0, 1, 3), listOf(0, 1, 3).map { editedOffset(before = before, after = after, offset = it) })
    }
}
