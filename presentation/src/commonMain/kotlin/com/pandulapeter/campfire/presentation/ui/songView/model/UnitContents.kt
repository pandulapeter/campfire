/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.model

import com.pandulapeter.campfire.presentation.ui.notation.NotationSystemPaint
import com.pandulapeter.campfire.presentation.ui.notation.SongNotations
import com.pandulapeter.campfire.presentation.ui.notation.abcTune
import com.pandulapeter.campfire.presentation.ui.songLayout.FoldableKind

/**
 * What a slot of a tune drawn as notation is measured by (`SongNotationBlock`): its [systems], compared and hashed by the
 * list, whose elements are compared by identity, so that one rendering is one value and a lookup never hashes a whole
 * drawing; the pixels to the unit; the widest and the narrowest column it asks for; and whether its heading holds a
 * part picker, which the heading's height depends on.
 */
internal data class NotationMeasureToken(
    val systems: List<NotationSystemPaint>,
    val unitSize: Float,
    val maxWidth: Float,
    val minWidth: Float,
    val hasPartPicker: Boolean,
)

/**
 * The content of every chunk of [units] of [sections], which their sizes are reused by ([UnitContent]): the folds of
 * [foldedSections] that change it, and of each slot of an ABC tune what it draws of [notations] (null where it is drawn
 * as its text), so that a rendering arriving measures again only the chunks that draw a tune.
 */
internal fun unitContentsOf(units: SongUnits, sections: List<RenderSection>, foldedSections: Set<String>, notations: SongNotations): List<UnitContent> {
    val foldedKeys = sections.map { foldedKeysOf(it, foldedSections) }
    return units.itemRanges.mapIndexed { unit, items ->
        val section = units.unitSections[unit]
        UnitContent(sections[section], items.first, items.last, foldedKeys[section], notationOf(sections[section], items, notations))
    }
}

/** What the tune slots among [items] of [section] draw, null where it holds none. */
private fun notationOf(section: RenderSection, items: IntRange, notations: SongNotations): List<NotationMeasureToken?>? {
    if (section !is RenderSection.Lines) return null
    val slots = items.mapNotNull { section.sectionItems.getOrNull(it) as? SectionItem.RunSlot }.filter { it.kind == FoldableKind.VERBATIM }
    val tunes = slots.mapNotNull { slot -> slot.run.abcTune()?.let { it to slot.tuneOffset } }
    if (tunes.isEmpty()) return null
    return tunes.map { (tune, tuneOffset) ->
        notations.of(tune, tuneOffset)?.let { run ->
            NotationMeasureToken(
                systems = run.systems,
                unitSize = run.unitSize,
                maxWidth = run.maxWidth,
                minWidth = run.minWidth,
                hasPartPicker = notations.partPickerOf(tune, tuneOffset) != null,
            )
        }
    }
}

/**
 * For each chunk of [units] of [sections], whether its height is the same at every width at or past its widest line, so
 * that it is measured once for all of them ([SectionMeasurements][com.pandulapeter.campfire.presentation.ui.songView.grid.SectionMeasurements]):
 * the lines, comments, key changes, tabs, grids, and headings of a section, whose widest line its content alone fixes,
 * and the comment, key change, and timing sections. Never a chunk holding a tune, which a leaving engraving made for a
 * wider page scales by the width past it, nor the Chords, Scales, or first sections, which no probe has proven flat.
 */
internal fun flatPastWidestOf(units: SongUnits, sections: List<RenderSection>) = BooleanArray(units.itemRanges.size) { unit ->
    when (val section = sections[units.unitSections[unit]]) {
        is RenderSection.Lines -> units.itemRanges[unit].none { (section.sectionItems.getOrNull(it) as? SectionItem.RunSlot)?.kind == FoldableKind.VERBATIM }
        is RenderSection.Comment, is RenderSection.KeyChange, is RenderSection.Timing -> true
        else -> false
    }
}
