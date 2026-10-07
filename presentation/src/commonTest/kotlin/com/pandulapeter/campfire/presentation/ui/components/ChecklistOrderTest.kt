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

class ChecklistOrderTest {
    private val rows = listOf("a", "b", "c", "d")

    @Test
    fun heldRowsLeadInTheRequestedSortingOrderAndAreNotRepeated() {
        val order = ChecklistOrder(setOf("d", "b"))
        assertEquals(listOf("b", "d"), order.selectedGroup(rows) { it })
        assertEquals(listOf("a", "c"), order.remainingRows(rows) { it })
        assertEquals(listOf("d", "b"), order.selectedGroup(rows.reversed()) { it })
    }

    @Test
    fun checkedRowsJoinTheGroupInTickOrderAndStayInPlace() {
        val order = ChecklistOrder(setOf("b")).withCheckedKeys(setOf("b", "d")).withCheckedKeys(setOf("b", "d", "a"))
        assertEquals(listOf("b", "d", "a"), order.selectedGroup(rows) { it })
        assertEquals(listOf("a", "c", "d"), order.remainingRows(rows) { it })
    }

    @Test
    fun theGroupOnlyGrowsUntilARefresh() {
        val checked = ChecklistOrder(setOf("b")).withCheckedKeys(setOf("b", "d"))
        val unchecked = checked.withCheckedKeys(emptySet())
        assertEquals(listOf("b", "d"), unchecked.selectedGroup(rows) { it })
        assertEquals(checked.remainingRows(rows) { it }, unchecked.remainingRows(rows) { it })
        // Checking a row again does not add a second copy or move the first.
        assertEquals(listOf("b", "d"), unchecked.withCheckedKeys(setOf("d", "b")).selectedGroup(rows) { it })
        // Search, sorting and filter changes reseed the order with only the currently checked keys.
        val refreshed = ChecklistOrder(setOf("d"))
        assertEquals(listOf("d"), refreshed.selectedGroup(rows) { it })
        assertEquals(listOf("a", "b", "c"), refreshed.remainingRows(rows) { it })
    }

    @Test
    fun layoutCountsTheHeadingTheGroupAndTheDivider() {
        assertEquals(0, ChecklistOrder(emptySet()).layout(rows) { it }.leadingRowCount)
        assertEquals(3, ChecklistOrder(setOf("b")).layout(rows) { it }.leadingRowCount)
        assertEquals(4, ChecklistOrder(setOf("b")).withCheckedKeys(setOf("b", "c")).layout(rows) { it }.leadingRowCount)
        // With nothing left under the group there is no divider.
        assertEquals(5, ChecklistOrder(rows.toSet()).layout(rows) { it }.leadingRowCount)
    }

    @Test
    fun theHeadingStaysWhileEverythingCheckedIsFilteredOut() {
        val order = ChecklistOrder(setOf("b", "d"))
        val filtered = listOf("a", "c")
        assertEquals(true, order.hasHeading(filtered) { it })
        assertEquals(emptyList(), order.selectedGroup(filtered) { it })
        // The heading and the divider under it.
        assertEquals(2, order.layout(filtered) { it }.leadingRowCount)
        assertEquals(false, ChecklistOrder(emptySet()).hasHeading(filtered) { it })
        // A row unchecked since the refresh keeps the heading over it, at a count of nothing.
        assertEquals(true, ChecklistOrder(setOf("a")).withCheckedKeys(emptySet()).hasHeading(rows) { it })
    }

    @Test
    fun filteredOutAndDeletedRowsNeverAppearInTheResults() {
        val order = ChecklistOrder(setOf("b", "d", "deleted")).withCheckedKeys(setOf("b", "d", "deleted", "hidden"))
        assertEquals(listOf("d"), order.selectedGroup(listOf("a", "d")) { it })
        assertEquals(listOf("a"), order.remainingRows(listOf("a", "d")) { it })
        assertEquals(emptyList(), order.selectedGroup(emptyList<String>()) { it })
    }

    @Test
    fun chipsLeadWithTheHeldOnesAndStayPut() {
        val order = ChecklistOrder(setOf("d", "b")).withCheckedKeys(setOf("d", "b", "a"))
        assertEquals(listOf("b", "d", "a", "c"), order.ordered(rows) { it })
        assertEquals(2, order.leadingCount(rows) { it })
    }
}
