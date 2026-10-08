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

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.presentation.ui.songLayout.FoldableKind
import com.pandulapeter.campfire.presentation.ui.songLayout.areAll

internal fun ChordProLine.foldableKind() = when (this) {
    is ChordProLine.Tab -> FoldableKind.TAB
    is ChordProLine.Grid -> FoldableKind.GRID
    is ChordProLine.Lyrics, ChordProLine.Blank -> null
}

/**
 * How many pieces a run of tablature or grid lines is drawn as, which the section may be cut between: a line of a grid
 * each, and for tablature as many as the rows it could wrap into (see [SongTabBlock]), which are only known once the
 * width is - a slot for every line and for every [MIN_TAB_SLOT_COLUMNS] characters of the longest, which no real width
 * wraps it into more rows than, and never more than [MAX_TAB_SLOTS], the last one holding whatever rows are left. A slot
 * with no row at a width has no height there, and nothing is cut in front of it.
 */
internal fun List<ChordProLine>.runSlotCount(kind: FoldableKind) = when (kind) {
    FoldableKind.TAB -> (size + maxOf { (it as? ChordProLine.Tab)?.text?.length ?: 0 } / MIN_TAB_SLOT_COLUMNS).coerceIn(1, MAX_TAB_SLOTS)
    FoldableKind.GRID -> count { it is ChordProLine.Grid }.coerceAtLeast(1)
}

/** The kind a section is written in from start to end, which is then folded as a whole, or null for any other. */
internal fun List<ChordProLine>.wholeFoldableKind() = when {
    areAll<ChordProLine.Tab>() -> FoldableKind.TAB
    areAll<ChordProLine.Grid>() -> FoldableKind.GRID
    else -> null
}

/** What the `{start_of_tab}` or `{start_of_grid}` a line was written in was labelled, which names its fold. */
internal val ChordProLine.environmentLabel
    get() = when (this) {
        is ChordProLine.Tab -> label
        is ChordProLine.Grid -> label
        is ChordProLine.Lyrics, ChordProLine.Blank -> null
    }

/**
 * Splits lines into runs of tablature, runs of grid lines and runs of everything else, keeping their order. Each of
 * the first two is folded as one, and tablature is also measured and scrolled as a block, so the lines of a run have to
 * reach the layout together. Two environments written one after the other are two runs where they were labelled
 * differently, since each is then folded under its own name.
 */
internal fun List<ChordProLine>.groupIntoRuns(): List<List<ChordProLine>> {
    val groups = mutableListOf<List<ChordProLine>>()
    var group = mutableListOf<ChordProLine>()
    forEach { line ->
        val first = group.firstOrNull()
        if (first != null && (first.foldableKind() != line.foldableKind() || first.environmentLabel != line.environmentLabel)) {
            groups += group
            group = mutableListOf()
        }
        group += line
    }
    if (group.isNotEmpty()) groups += group
    return groups
}

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

/** Whether a run (or a section that is nothing else) is unfolded, and how to fold or unfold it. */
internal class FoldToggle(
    val isExpanded: Boolean,
    val onToggled: () -> Unit,
)

/**
 * Fades a run of tablature, a grid or the song's info card in as it is unfolded, while the section around it grows to
 * make room on its own spring (`animateBounds`). Only the opacity is animated, never the size: the column layout decides where every
 * section goes from their intrinsic heights, and a run whose height was still on its way would be measured halfway
 * there.
 */
@Composable
internal fun Modifier.fadingIn(isFadingIn: Boolean): Modifier {
    val alpha = remember { Animatable(if (isFadingIn) 0f else 1f) }
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(alpha) { alpha.animateTo(1f, spec) }
    return graphicsLayer { this.alpha = alpha.value }
}

internal val FOLD_CHEVRON_SIZE = 20.dp
internal val FOLD_CHEVRON_GAP = 4.dp

private const val MIN_TAB_SLOT_COLUMNS = 12
private const val MAX_TAB_SLOTS = 24
