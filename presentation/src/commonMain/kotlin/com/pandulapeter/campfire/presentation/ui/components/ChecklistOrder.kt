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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Checked and recently unchecked rows share the leading group. Newly checked rows go before that whole group,
 * most recent first, and stay there after unchecking. Refreshing reseeds the group in the caller's sorting order.
 */
internal data class ChecklistOrder(
    val heldKeys: Set<String>,
    val promotedKeys: List<String> = emptyList(),
    val checkedKeys: Set<String> = heldKeys,
) {
    fun withCheckedKeys(checkedKeys: Set<String>): ChecklistOrder {
        val newlyChecked = checkedKeys - this.checkedKeys
        return ChecklistOrder(
            heldKeys = heldKeys + checkedKeys,
            promotedKeys = newlyChecked.toList().asReversed() + promotedKeys.filterNot { it in newlyChecked },
            checkedKeys = checkedKeys,
        )
    }

    fun <T> ordered(items: List<T>, key: (T) -> String): List<T> {
        val byKey = items.associateBy(key)
        val promoted = promotedKeys.toSet()
        val (leading, remaining) = items.filterNot { key(it) in promoted }.partition { key(it) in heldKeys }
        return promotedKeys.mapNotNull { byKey[it] } + leading + remaining
    }

    fun <T> leadingCount(items: List<T>, key: (T) -> String) = items.count { key(it) in heldKeys }
}

/** A divider appears only where the promoted group meets rows that follow the selected sorting order. */
internal fun <T> LazyListScope.checklistItems(
    items: List<T>,
    order: ChecklistOrder,
    key: (T) -> String,
    itemContent: @Composable LazyItemScope.(T) -> Unit,
) {
    val leadingCount = order.leadingCount(items, key)
    // Namespace row keys so a user-created tag cannot collide with the divider or other list actions.
    val rowKey: (T) -> String = { "checklist:${key(it)}" }
    items(items.take(leadingCount), key = rowKey, itemContent = itemContent)
    if (leadingCount > 0 && leadingCount < items.size) {
        item(key = "checklist_divider") { HorizontalDivider(modifier = Modifier.fillMaxWidth()) }
    }
    items(items.drop(leadingCount), key = rowKey, itemContent = itemContent)
}

/** Retains unchecked rows across selection edits and Activity recreation, until search, sorting or filters change. */
@Composable
internal fun rememberChecklistOrder(checkedKeys: Set<String>, refreshKey: Any?): ChecklistOrder {
    var savedOrder by rememberSaveable(
        refreshKey,
        stateSaver = mapSaver(
            save = { mapOf("held" to it.heldKeys.toList(), "promoted" to it.promotedKeys, "checked" to it.checkedKeys.toList()) },
            restore = { saved ->
                ChecklistOrder(
                    heldKeys = (saved.getValue("held") as List<*>).filterIsInstance<String>().toSet(),
                    promotedKeys = (saved.getValue("promoted") as List<*>).filterIsInstance<String>(),
                    checkedKeys = (saved.getValue("checked") as List<*>).filterIsInstance<String>().toSet(),
                )
            },
        ),
    ) { mutableStateOf(ChecklistOrder(checkedKeys)) }
    val order = remember(savedOrder, checkedKeys) { savedOrder.withCheckedKeys(checkedKeys) }
    SideEffect { savedOrder = order }
    return order
}

/**
 * Keep the viewport at its old position through the reorder, then animate to the newly checked first row.
 * Otherwise lazy-list key anchoring would follow the moved row immediately and leave no distance to animate.
 * Headers and the tag-creation action are accounted for by [rowOffset].
 */
@Composable
internal fun ScrollToNewlyCheckedItem(
    listState: LazyListState,
    checkedKeys: Set<String>,
    orderedKeys: List<String>,
    rowOffset: Int = 0,
) {
    val selection = remember(listState) { ChecklistSelection(checkedKeys) }
    val scope = rememberCoroutineScope()
    val scroll = remember(listState) { ChecklistScroll() }
    SideEffect {
        selection.newlyCheckedIndex(checkedKeys, orderedKeys, rowOffset)?.let { index ->
            scroll.job?.cancel()
            listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            scroll.job = scope.launch {
                // Let the new ordering be measured before the scroll uses its indices.
                withFrameNanos { }
                listState.animateScrollToItem(index)
            }
        }
    }
}

private class ChecklistScroll(var job: Job? = null)

/** Tracks selection changes independently of sorting and filtering; an uncheck never requests a scroll. */
internal class ChecklistSelection(private var keys: Set<String>) {
    fun newlyCheckedIndex(checkedKeys: Set<String>, orderedKeys: List<String>, rowOffset: Int = 0): Int? {
        val newlyChecked = checkedKeys - keys
        keys = checkedKeys
        return orderedKeys.indexOfFirst { it in newlyChecked }.takeIf { it >= 0 }?.plus(rowOffset)
    }
}
