/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import com.pandulapeter.campfire.presentation.ui.chords.ChordCell

/**
 * This list with the items [isFirst] says first and the rest after them, each part in the order it had: the dock's
 * chords and scales on the screen brought before the dimmed ones, and nothing else moved.
 */
internal fun <T> List<T>.partitionedStably(isFirst: (T) -> Boolean): List<T> = filter(isFirst) + filterNot(isFirst)

/**
 * The dock's chord [cells] with the ones played on the screen ([onScreen]) first and the dimmed ones after, each in the
 * order the song first plays them. A song in several keys keeps each key's chords under its label, so the chords are
 * brought forward within their key. Nothing moves while no chord is known to be on the screen.
 */
internal fun dockCellOrder(cells: List<ChordCell>, onScreen: Set<String>): List<ChordCell> =
    if (onScreen.isEmpty()) cells else cells.partitionedWithinRuns(runOf = { it.keyName }) { it.name in onScreen }

/** [partitionedStably] within each run of neighbours [runOf] says belong together, the runs kept in their order. */
internal fun <T> List<T>.partitionedWithinRuns(runOf: (T) -> Any?, isFirst: (T) -> Boolean): List<T> {
    val runs = mutableListOf<MutableList<T>>()
    forEach { item ->
        val run = runs.lastOrNull()?.takeIf { runOf(it.first()) == runOf(item) }
        if (run == null) runs.add(mutableListOf(item)) else run.add(item)
    }
    return runs.flatMap { it.partitionedStably(isFirst) }
}

/**
 * The dock's rows of scale chips ([rows], each of the group [groupOf] says) in the order they are drawn: the home group's
 * first whatever is on the screen, since it has no title to be told apart by anywhere else, then the other groups on the
 * screen ([onScreen]) and the dimmed ones after them, each in the order the song's Scales section has them, a group's
 * rows kept together. Nothing moves while no range is known (null).
 */
internal fun <T> dockScaleRowOrder(rows: List<T>, groupOf: (T) -> Int, isHome: (Int) -> Boolean, onScreen: Set<Int>?): List<T> {
    if (onScreen == null) return rows
    val (home, others) = rows.partition { isHome(groupOf(it)) }
    return home + others.partitionedStably { groupOf(it) in onScreen }
}
