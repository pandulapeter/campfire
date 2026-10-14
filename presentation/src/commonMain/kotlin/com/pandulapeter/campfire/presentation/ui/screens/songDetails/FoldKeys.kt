/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import com.pandulapeter.campfire.presentation.ui.songLayout.FoldableKind

/**
 * Which sections, tabs and grids of the song are folded away: [collapsed] is what the preferences hold for it, and a
 * toggle goes to [onToggled] to be saved there. What the page keeps of its own is which keys were [toggled] while it
 * was open, so that only those fade in as they unfold: a page opening onto a section that fades in would be an
 * animation nobody asked for.
 *
 * A section is keyed by what the file calls it and which of the sections called that it is (`chorus#2`, see
 * [RenderSection.Lines.foldKey]), and a run inside it by the section's key and the same two things of the run
 * (`intro#1/picking pattern#1`), never by its position on the page, so that what is saved still names the same part of
 * the song after a verse was added above it. A key that names nothing any more (the section was renamed or removed)
 * simply unfolds nothing.
 */
internal class FoldedRuns(
    private val collapsed: Set<String>,
    private val toggled: Set<String>,
    private val onToggled: (key: String) -> Unit,
) {

    fun isCollapsed(key: String) = key in collapsed

    fun hasBeenToggled(key: String) = key in toggled

    fun toggle(key: String) = onToggled(key)
}

/** The next key of a fold named [name] where the ones before it were counted in this map: `name#1`, `name#2`… */
internal fun MutableMap<String, Int>.nextFoldKey(name: String): String {
    val occurrence = (this[name] ?: 0) + 1
    this[name] = occurrence
    return "$name#$occurrence"
}

/**
 * The key of the next run of [kind] inside the section folded as [sectionFold], named by its label or, unlabelled, by
 * its kind (`intro#1/tab#2`). Saved in the preferences, so its form is the one every key already saved there has.
 */
internal fun MutableMap<String, Int>.nextRunFoldKey(sectionFold: String, label: String?, kind: FoldableKind): String {
    val runName = label ?: kind.name.lowercase()
    return "$sectionFold/${nextFoldKey(runName)}"
}

/**
 * The keys of [folded] that fold [section] or one of its runs (`intro#1`, `intro#1/tab#1`), which is all of the
 * folds that can change its height. A key under it that names no run of it (the rest of a chorus recall, keyed
 * `chorus#1/1`) only costs it a measurement it did not need.
 */
internal fun foldedKeysOf(section: RenderSection, folded: Set<String>): Set<String> = when (section) {
    is RenderSection.Lines -> folded.filterTo(HashSet()) { it == section.foldKey || it.startsWith("${section.foldKey}/") }
    else -> emptySet()
}

/** Whether a run (or a section that is nothing else) is unfolded, and how to fold or unfold it. */
internal class FoldToggle(
    val isExpanded: Boolean,
    val onToggled: () -> Unit,
)
