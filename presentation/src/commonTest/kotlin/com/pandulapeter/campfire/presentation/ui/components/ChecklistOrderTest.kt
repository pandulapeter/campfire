/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChecklistOrderTest {
    private val rows = listOf("a", "b", "c", "d")

    @Test
    fun checkedRowsLeadInTheRequestedSortingOrder() {
        val order = ChecklistOrder(setOf("d", "b"))
        assertEquals(listOf("b", "d", "a", "c"), order.ordered(rows) { it })
        assertEquals(listOf("d", "b", "c", "a"), order.ordered(rows.reversed()) { it })
    }

    @Test
    fun checkingPromotesAndUncheckingKeepsTheRowInPlace() {
        val initial = ChecklistOrder(setOf("b"))
        val checked = initial.withCheckedKeys(setOf("b", "d"))
        val unchecked = checked.withCheckedKeys(setOf("d"))
        assertEquals(listOf("d", "b", "a", "c"), checked.ordered(rows) { it })
        assertEquals(checked.ordered(rows) { it }, unchecked.ordered(rows) { it })
        // Search, sorting and filter changes reseed the order with only the currently checked keys.
        val refreshed = ChecklistOrder(setOf("d"))
        assertEquals(listOf("d", "a", "b", "c"), refreshed.ordered(rows) { it })
    }

    @Test
    fun newlyCheckedRowsLeadAndRecheckingReturnsTheRowToPositionZero() {
        val first = ChecklistOrder(setOf("b")).withCheckedKeys(setOf("b", "d"))
        val second = first.withCheckedKeys(setOf("b", "d", "a"))
        assertEquals(listOf("a", "d", "b", "c"), second.ordered(rows) { it })
        val unchecked = second.withCheckedKeys(setOf("b", "a"))
        assertEquals(second.ordered(rows) { it }, unchecked.ordered(rows) { it })
        val rechecked = unchecked.withCheckedKeys(setOf("b", "a", "d"))
        assertEquals(listOf("d", "a", "b", "c"), rechecked.ordered(rows) { it })
        // A refresh removes the temporary recency order as well as retaining only the checked rows.
        assertEquals(listOf("a", "b", "d", "c"), ChecklistOrder(setOf("b", "a", "d")).ordered(rows) { it })
    }

    @Test
    fun dividerBoundaryCountsOnlyVisibleLeadingRows() {
        val order = ChecklistOrder(setOf("b", "d", "hidden")).withCheckedKeys(setOf("b", "d", "hidden", "a"))
        assertEquals(3, order.leadingCount(rows) { it })
        assertEquals(1, order.leadingCount(listOf("c", "d")) { it })
        assertEquals(0, order.leadingCount(listOf("c")) { it })
        assertEquals(0, order.leadingCount(emptyList<String>()) { it })
        assertEquals(2, order.leadingCount(listOf("b", "d")) { it })
    }

    @Test
    fun filteredOutAndDeletedRowsNeverAppearInTheResults() {
        val order = ChecklistOrder(setOf("b", "d", "deleted"))
        assertEquals(listOf("d", "a"), order.ordered(listOf("a", "d")) { it })
        assertEquals(emptyList(), order.ordered(emptyList<String>()) { it })
    }

    @Test
    fun checkingScrollsToThePromotedRowIncludingLeadingActions() {
        val selection = ChecklistSelection(setOf("b"))
        assertEquals(1, selection.newlyCheckedIndex(setOf("b", "d"), listOf("d", "b", "a", "c"), rowOffset = 1))
        assertNull(selection.newlyCheckedIndex(setOf("b", "d"), listOf("d", "b", "a", "c"), rowOffset = 1))
    }

    @Test
    fun openingRefreshingAndUncheckingNeverRequestAScroll() {
        val selection = ChecklistSelection(setOf("b", "d"))
        assertNull(selection.newlyCheckedIndex(setOf("b", "d"), rows))
        assertNull(selection.newlyCheckedIndex(setOf("b", "d"), rows.reversed()))
        assertNull(selection.newlyCheckedIndex(setOf("d"), listOf("b", "d", "a", "c")))
        assertEquals(0, selection.newlyCheckedIndex(setOf("b", "d"), listOf("b", "d", "a", "c")))
    }

    @Test
    fun asyncSelectionUpdatesScrollOnlyWhenTheCheckedRowIsVisible() {
        val selection = ChecklistSelection(emptySet())
        assertEquals(0, selection.newlyCheckedIndex(setOf("new-setlist"), listOf("new-setlist", "a")))
        assertNull(selection.newlyCheckedIndex(setOf("new-setlist", "hidden"), listOf("new-setlist", "a")))
    }
}
