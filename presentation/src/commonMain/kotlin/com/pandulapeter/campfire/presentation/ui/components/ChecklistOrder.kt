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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.checklist_selected

/**
 * The order of a checklist in a sheet: a selected group at the top, and under it every other row in the caller's
 * sorting order.
 *
 * The group starts as the rows that were checked when the list was last refreshed ([heldKeys]), in the sorting order.
 * A row checked after that joins the end of the group as a copy ([addedKeys], in the order they were ticked) while it
 * also stays where it was tapped, so the list never moves under the finger and the whole selection can still be read
 * at the top. The group only grows until the next refresh: a row unchecked there stays, unchecked, rather than taking
 * the rows after it up into its place. Opening the sheet, searching, sorting or filtering again reseeds it from what
 * is checked by then.
 */
internal data class ChecklistOrder(
    val heldKeys: Set<String>,
    val addedKeys: List<String> = emptyList(),
    val checkedKeys: Set<String> = heldKeys,
) {
    fun withCheckedKeys(checkedKeys: Set<String>): ChecklistOrder {
        val added = (checkedKeys - this.checkedKeys).filterNot { it in heldKeys || it in addedKeys }
        return copy(addedKeys = addedKeys + added, checkedKeys = checkedKeys)
    }

    /** The rows of the selected group that [items] still holds. */
    fun <T> selectedGroup(items: List<T>, key: (T) -> String): List<T> {
        val byKey = items.associateBy(key)
        return items.filter { key(it) in heldKeys } + addedKeys.mapNotNull { key -> byKey[key]?.takeIf { key !in heldKeys } }
    }

    /** Every row outside the held group, the ones added to the group since the last refresh included. */
    fun <T> remainingRows(items: List<T>, key: (T) -> String) = items.filterNot { key(it) in heldKeys }

    /**
     * Whether [checklistItems] draws its heading: while anything is checked, filtered out of [items] or not, so that
     * the count does not come and go with the search, and while the group still holds a row that was unchecked.
     */
    fun <T> hasHeading(items: List<T>, key: (T) -> String) = checkedKeys.isNotEmpty() || selectedGroup(items, key).isNotEmpty()

    /** The rows [checklistItems] draws before [remainingRows]: the heading, the selected group and the divider. */
    fun <T> layout(items: List<T>, key: (T) -> String): ChecklistLayout {
        val remainingRows = remainingRows(items, key)
        val leadingRowCount = if (hasHeading(items, key)) {
            1 + selectedGroup(items, key).size + if (remainingRows.isEmpty()) 0 else 1
        } else {
            0
        }
        return ChecklistLayout(
            heldKeys = heldKeys,
            addedKeys = addedKeys,
            leadingRowCount = leadingRowCount,
            remainingKeys = remainingRows.map(key),
        )
    }

    /** A sideways row of chips has no room for copies: the held chips lead it, and a chip that is tapped stays put. */
    fun <T> ordered(items: List<T>, key: (T) -> String): List<T> {
        val (leading, remaining) = items.partition { key(it) in heldKeys }
        return leading + remaining
    }

    fun <T> leadingCount(items: List<T>, key: (T) -> String) = items.count { key(it) in heldKeys }
}

/**
 * What [KeepChecklistRowsInPlace] compares between two compositions. A tick is what grows [addedKeys] with the same
 * [heldKeys]; a refresh never does, since it reseeds the order with no [addedKeys] at all, even where it leaves
 * [heldKeys] as they were and only changes how many of them [leadingRowCount] still counts.
 */
internal data class ChecklistLayout(
    val heldKeys: Set<String>,
    val addedKeys: List<String>,
    val leadingRowCount: Int,
    val remainingKeys: List<String>,
)

/**
 * Where the row [anchorKey], at [anchorIndex] in [previous], has to be put to stay under the finger, or null when nothing
 * has to move.
 *
 * Only a tick is answered: a refresh is the list going back to its start, and a sync that changes the rows without a
 * tick is data arriving, which the lazy list's own keyed anchoring is left to. The new index is counted from where the
 * checklist starts, so items before it (a filter header) are left out of the arithmetic, plus the anchor's new place
 * among the remaining rows, so a checked row arriving above it (a setlist created with the song in it) is accounted
 * for. A key can join [addedKeys] without being listed (a sync putting the song into a setlist the search hides), and
 * the anchor's own index is null too, since requesting it again from the last measured layout would only fight a
 * scroll in progress.
 */
internal fun ChecklistLayout.anchorIndexAfter(previous: ChecklistLayout, anchorKey: String, anchorIndex: Int): Int? {
    if (heldKeys != previous.heldKeys || addedKeys.size <= previous.addedKeys.size) return null
    val previousPosition = previous.remainingKeys.indexOf(anchorKey)
    val position = remainingKeys.indexOf(anchorKey)
    if (previousPosition == -1 || position == -1) return null
    return (anchorIndex - previous.leadingRowCount - previousPosition + leadingRowCount + position).takeIf { it != anchorIndex }
}

/**
 * The selected group under a heading that counts everything checked, the rows a search or a filter hides included, so
 * the number only changes with the selection; then a divider, then the rest of the rows ([ChecklistOrder]). The keys are namespaced so that a user-created tag cannot collide with the heading, the divider
 * or another action of the list, and so that a row's copy in the group is a different item from the row itself.
 */
internal fun <T> LazyListScope.checklistItems(
    items: List<T>,
    order: ChecklistOrder,
    key: (T) -> String,
    listState: LazyListState,
    horizontalInset: Dp = 0.dp,
    itemContent: @Composable LazyItemScope.(T) -> Unit,
) {
    val group = order.selectedGroup(items, key)
    val remaining = order.remainingRows(items, key)
    if (order.hasHeading(items, key)) {
        item(key = "checklist_heading") {
            SettingsSectionTitle(
                modifier = listItemAnimation(listState),
                text = stringResource(Res.string.checklist_selected, order.checkedKeys.size),
                contentPadding = PaddingValues(
                    start = horizontalInset + LIST_ITEM_KEYLINE,
                    end = horizontalInset + LIST_ITEM_KEYLINE,
                    top = 8.dp,
                    bottom = 4.dp,
                ),
            )
        }
        items(group, key = { "$SELECTED_ROW_KEY_PREFIX${key(it)}" }, itemContent = itemContent)
        if (remaining.isNotEmpty()) {
            item(key = "checklist_divider") { HorizontalDivider(modifier = Modifier.fillMaxWidth()) }
        }
    }
    items(remaining, key = { "$ROW_KEY_PREFIX${key(it)}" }, itemContent = itemContent)
}

/** Keeps the selected group across selection edits and Activity recreation, until search, sorting or filters change. */
@Composable
internal fun rememberChecklistOrder(checkedKeys: Set<String>, refreshKey: Any?): ChecklistOrder {
    var savedOrder by rememberSaveable(
        refreshKey,
        stateSaver = mapSaver(
            save = { mapOf("held" to it.heldKeys.toList(), "added" to it.addedKeys, "checked" to it.checkedKeys.toList()) },
            restore = { saved ->
                ChecklistOrder(
                    heldKeys = (saved.getValue("held") as List<*>).filterIsInstance<String>().toSet(),
                    addedKeys = (saved.getValue("added") as List<*>).filterIsInstance<String>(),
                    checkedKeys = (saved.getValue("checked") as List<*>).filterIsInstance<String>().toSet(),
                )
            },
        ),
    ) { mutableStateOf(ChecklistOrder(checkedKeys)) }
    val order = remember(savedOrder, checkedKeys) { savedOrder.withCheckedKeys(checkedKeys) }
    SideEffect { savedOrder = order }
    return order
}

internal const val ROW_KEY_PREFIX = "checklist_row:"
private const val SELECTED_ROW_KEY_PREFIX = "checklist_selected:"
