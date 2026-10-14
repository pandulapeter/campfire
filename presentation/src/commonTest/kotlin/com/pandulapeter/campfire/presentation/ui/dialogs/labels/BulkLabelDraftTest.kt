/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.labels

import com.pandulapeter.campfire.presentation.ui.dialogs.forms.label
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.state.ToggleableState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BulkLabelDraftTest {

    private val draft = BulkLabelDraft.of(listOf(listOf("rock", "live"), listOf("rock"), listOf("rock", "folk")))

    @Test
    fun `a row is on where every song has it, half ticked where some do and off where none does`() {
        assertEquals(ToggleableState.On, draft.stateOf("rock"))
        assertEquals(ToggleableState.Indeterminate, draft.stateOf("live"))
        assertEquals(ToggleableState.Off, draft.stateOf("jazz"))
        assertFalse(draft.hasChanges)
    }

    @Test
    fun `a row that started half ticked goes on, off and back`() {
        val on = draft.toggled("live")
        val off = on.toggled("live")
        val back = off.toggled("live")
        assertEquals(listOf(ToggleableState.On, ToggleableState.Off, ToggleableState.Indeterminate), listOf(on, off, back).map { it.stateOf("live") })
        assertEquals(setOf("live"), on.added)
        assertEquals(setOf("live"), off.removed)
        assertFalse(back.hasChanges)
    }

    @Test
    fun `the other rows only go on and off`() {
        assertEquals(ToggleableState.Off, draft.toggled("rock").stateOf("rock"))
        assertEquals(ToggleableState.On, draft.toggled("rock").toggled("rock").stateOf("rock"))
        assertEquals(ToggleableState.On, draft.toggled("jazz").stateOf("jazz"))
        assertEquals(ToggleableState.Off, draft.toggled("jazz").toggled("jazz").stateOf("jazz"))
        assertFalse(draft.toggled("jazz").toggled("jazz").hasChanges)
    }

    @Test
    fun `added and removed name only what changed`() {
        val changed = draft.toggled("rock").toggled("jazz")
        assertEquals(setOf("jazz"), changed.added)
        assertEquals(setOf("rock"), changed.removed)
        assertTrue(changed.hasChanges)
    }

    @Test
    fun `an entered label is on`() = assertEquals(setOf("new"), draft.checked("new").added)

    @Test
    fun `the ticked rows lead, half ticked ones included`() {
        assertEquals(setOf("rock", "live", "folk"), draft.checkedKeys)
        assertEquals(setOf("rock"), draft.onKeys)
    }

    @Test
    fun `one song never has a half ticked row`() {
        val single = BulkLabelDraft.of(listOf(listOf("rock", "live")))
        assertEquals(setOf("rock", "live"), single.onKeys)
        assertEquals(single.onKeys, single.checkedKeys)
    }

    @Test
    fun `a saved draft comes back as it was`() {
        val changed = draft.toggled("live").toggled("rock").checked("new")
        val saved = with(BulkLabelDraft.saver) { SaverScope { true }.save(changed) }!!
        assertEquals(changed, BulkLabelDraft.saver.restore(saved))
    }
}
