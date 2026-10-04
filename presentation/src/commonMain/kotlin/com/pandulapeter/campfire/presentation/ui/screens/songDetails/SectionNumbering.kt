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

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.SectionType

/**
 * [this] with the sections the file gives no label numbered by their kind ("Verse 1", "Verse 2"), so that a library
 * whose files name nothing still reads the way one written with labels did, and the kind's name stays translatable.
 *
 * A kind is only numbered where the song has more than one section of it without a label: a lone verse is just
 * "Verse". A section with a label of its own keeps it and takes no number, a paragraph is not a kind, and the rest of
 * a section a comment cut in two is the section it continues. A `{chorus}` recall that names nothing is headed by the
 * chorus it repeats, so the first section the recall repeats (its opening comments come before it) carries the
 * number of the most recent one. The number is a field of the section ([ChordProBlock.Section.number]) rather than
 * part of its label, which stays what the file wrote: the saved folds are keyed by it, and they must not move when the
 * setting is turned on or off.
 */
internal fun List<ChordProBlock>.withNumberedSections(labels: DefaultSectionLabels): List<ChordProBlock> {
    if (!labels.shouldNumberSections) return this
    val counts = filterIsInstance<ChordProBlock.Section>().filter { it.isNumbered() }.groupingBy { it.type.numberingKey }.eachCount()
    if (counts.values.none { it > 1 }) return this
    val numbers = mutableMapOf<String, Int>()
    var lastChorusNumber: Int? = null
    return map { block ->
        when {
            block is ChordProBlock.Section && block.isNumbered() -> {
                val key = block.type.numberingKey
                val number = numbers.getOrElse(key) { 0 } + 1
                numbers[key] = number
                val numbered = if (counts.getValue(key) > 1) block.copy(number = number) else block
                if (block.type == SectionType.Chorus) lastChorusNumber = numbered.number
                numbered
            }

            block is ChordProBlock.Section -> {
                if (block.type == SectionType.Chorus && !block.isContinuation) lastChorusNumber = null
                block
            }

            block is ChordProBlock.ChorusRecall && block.label == null && lastChorusNumber != null -> {
                val sectionIndex = block.blocks.indexOfFirst { it is ChordProBlock.Section }
                val first = block.blocks.getOrNull(sectionIndex) as? ChordProBlock.Section
                if (first != null && first.label == null && first.number == null) {
                    block.copy(blocks = block.blocks.mapIndexed { index, piece -> if (index == sectionIndex) first.copy(number = lastChorusNumber) else piece })
                } else {
                    block
                }
            }

            else -> block
        }
    }
}

/** Whether the section is headed by its kind's name, which numbering can then add to. */
private fun ChordProBlock.Section.isNumbered() = label == null && !isContinuation && type != SectionType.Paragraph

/** One kind of section: the names of the custom ones are compared the way [DefaultSectionLabels.labelOf] reads them. */
private val SectionType.numberingKey
    get() = when (this) {
        is SectionType.Custom -> name.lowercase()
        else -> toString()
    }

/** [this] followed by [number] where there is one. */
internal fun String.withNumber(number: Int?) = if (number == null) this else "$this $number"
