/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import com.pandulapeter.campfire.presentation.ui.dialogs.forms.label
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.state.ToggleableState

/**
 * What the tag or the language sheet has ticked for the songs it was opened on, one box per label, keyed by the label
 * as the files are compared by it (a tag folded by case, a language by its code).
 *
 * A row starts on where every song carries the label, off where none does, and half ticked where only some do. A row
 * that started half ticked goes on, off and back to half ticked as it is tapped, the last of which leaves it alone in
 * every file; the others only ever go on and off. For one song no row is ever half ticked, so the sheet reads as it
 * always did.
 */
internal data class BulkLabelDraft(
    private val initial: Map<String, ToggleableState>,
    private val current: Map<String, ToggleableState> = initial,
) {
    fun stateOf(key: String) = current[key] ?: ToggleableState.Off

    fun toggled(key: String): BulkLabelDraft {
        val next = when (stateOf(key)) {
            ToggleableState.Indeterminate -> ToggleableState.On
            ToggleableState.On -> ToggleableState.Off
            ToggleableState.Off -> if (initial[key] == ToggleableState.Indeterminate) ToggleableState.Indeterminate else ToggleableState.On
        }
        return copy(current = current + (key to next))
    }

    /** The row for [key] on, which is what a label typed into the field and entered asks for. */
    fun checked(key: String) = copy(current = current + (key to ToggleableState.On))

    /** The labels that are now on every song and were not when the sheet opened. */
    val added: Set<String> get() = current.filter { (key, state) -> state == ToggleableState.On && initial[key] != ToggleableState.On }.keys

    /** The labels that are now on no song and were on some or all of them when the sheet opened. */
    val removed: Set<String>
        get() = current.filter { (key, state) -> state == ToggleableState.Off && (initial[key] ?: ToggleableState.Off) != ToggleableState.Off }.keys

    val hasChanges get() = added.isNotEmpty() || removed.isNotEmpty()

    /** The rows with a tick of any kind, which lead the list the way the ticked ones of a checklist do. */
    val checkedKeys: Set<String> get() = current.filterValues { it != ToggleableState.Off }.keys

    /** The rows that are on, which for one song is the whole of what it is to carry. */
    val onKeys: Set<String> get() = current.filterValues { it == ToggleableState.On }.keys

    companion object {

        /** The draft for songs that carry [labelsPerSong], one collection of keys per song. */
        fun of(labelsPerSong: List<Collection<String>>): BulkLabelDraft {
            val counts = mutableMapOf<String, Int>()
            labelsPerSong.forEach { labels -> labels.toSet().forEach { counts[it] = (counts[it] ?: 0) + 1 } }
            return BulkLabelDraft(
                counts.mapValues { (_, count) -> if (count == labelsPerSong.size) ToggleableState.On else ToggleableState.Indeterminate },
            )
        }

        /** Keeps a draft across a recreated Activity, since Save writes whatever is ticked at that moment. */
        val saver = Saver<BulkLabelDraft, List<String>>(
            save = { draft ->
                (draft.initial.keys + draft.current.keys).flatMap { key ->
                    listOf(key, draft.initial[key]?.name.orEmpty(), draft.current[key]?.name.orEmpty())
                }
            },
            restore = { saved ->
                val rows = saved.chunked(3)
                fun stateMap(index: Int) = rows.mapNotNull { row ->
                    row[index].takeIf { it.isNotEmpty() }?.let { row[0] to ToggleableState.valueOf(it) }
                }.toMap()
                BulkLabelDraft(initial = stateMap(1), current = stateMap(2))
            },
        )
    }
}
