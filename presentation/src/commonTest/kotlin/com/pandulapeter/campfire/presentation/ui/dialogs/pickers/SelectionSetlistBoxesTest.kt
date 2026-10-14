/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.pickers

import androidx.compose.ui.state.ToggleableState
import com.pandulapeter.campfire.data.model.setlist.Setlist
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** The selection's Choose setlists sheet reads each setlist's box from what it held of the selection when first tapped. */
internal class SelectionSetlistBoxesTest {

    private val setlist = Setlist(
        fileName = "set.setlist.json",
        title = "Set",
        description = "",
        date = LocalDate(2026, 1, 1),
        isArchived = false,
        entries = listOf(
            Setlist.Entry(songFileName = "a"),
            Setlist.Entry(songFileName = "b", transposition = 2),
            Setlist.Entry(songFileName = "c"),
        ),
        size = 0,
    )

    private val selection = setOf("a", "b", "c")

    @Test
    fun `a selection box is ticked, empty or half ticked by what the setlist holds of the selection`() {
        val selection = setOf("a", "b")
        assertEquals(ToggleableState.Off, selectionBoxState(selection, held = emptySet(), added = emptySet()))
        assertEquals(ToggleableState.Indeterminate, selectionBoxState(selection, held = setOf("a"), added = emptySet()))
        assertEquals(ToggleableState.On, selectionBoxState(selection, held = setOf("a"), added = setOf("b")))
        assertEquals(ToggleableState.On, selectionBoxState(selection, held = setOf("a", "b", "x"), added = emptySet()))
    }

    @Test
    fun `a song both held and added counts once`() = assertEquals(
        ToggleableState.Indeterminate,
        selectionBoxState(setOf("a", "b"), held = setOf("a"), added = setOf("a")),
    )

    @Test
    fun `an added song outside the selection is not counted`() {
        val selection = setOf("a", "b")
        assertEquals(ToggleableState.Off, selectionBoxState(selection, held = emptySet(), added = setOf("x")))
        assertEquals(ToggleableState.Indeterminate, selectionBoxState(selection, held = setOf("a"), added = setOf("x")))
    }

    @Test
    fun `a setlist the sheet has not tapped is read as it is now`() {
        val held = heldOf(emptyMap(), setlistOf("a", "b", "c", "x"), selection)

        assertEquals(setOf("a", "b", "c"), held)
        assertEquals(ToggleableState.On, selectionBoxState(selection, held, emptySet()))
    }

    @Test
    fun `a recorded setlist is not read again`() = assertEquals(
        setOf("a"),
        heldOf(mapOf(setlist.fileName to setOf("a")), setlistOf("a", "b"), selection),
    )

    @Test
    fun `a tap records what the setlist holds then, once`() {
        val recorded = withHeldRecorded(emptyMap(), setlistOf("a"), selection)

        assertEquals(mapOf(setlist.fileName to setOf("a")), recorded)
        assertSame(recorded, withHeldRecorded(recorded, setlistOf("a", "b", "c"), selection))
    }

    private fun setlistOf(vararg songFileNames: String) = setlist.copy(entries = songFileNames.map { Setlist.Entry(songFileName = it) })
}
