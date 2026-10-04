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
 * A kind is only numbered where the song has more than one section of it: a lone verse is just "Verse". A label that
 * is nothing but the kind's name makes its section one of the kind too, so that no two sections share a number: a bare
 * "Verse" is numbered with the unlabelled ones and keeps its label, shown with the number after it, and "Verse 2" keeps
 * its label and the number it names, which the others skip. Any other label takes no part, a paragraph is not a kind,
 * and the rest of a section a comment cut in two is the section it continues. The kind's name is the heading the app
 * gives it or its ChordPro name, so a label in the app's other language counts only while the app is in that language
 * — the ChordPro name counts in both — and switching the language can change which numbers the other sections get;
 * the folds stay put, since they are keyed by the label as written. A `{chorus}` recall that names nothing is headed
 * by the chorus it repeats, so the first section the recall repeats (its opening comments come before it) carries the
 * number of the most recent one. The number is a field of the section ([ChordProBlock.Section.number]) rather than
 * part of its label, which stays what the file wrote: the saved folds are keyed by it, and they must not move when the
 * setting is turned on or off.
 */
internal fun List<ChordProBlock>.withNumberedSections(labels: DefaultSectionLabels): List<ChordProBlock> {
    if (!labels.shouldNumberSections) return this
    val kinds = filterIsInstance<ChordProBlock.Section>().filter { it.isOfAKind() }.map { it to it.kindLabel(labels) }
    val counts = kinds.filter { (section, kindLabel) -> section.isNumbered(kindLabel) || kindLabel is KindLabel.Numbered }
        .groupingBy { (section, _) -> section.type.numberingKey }.eachCount()
    if (counts.values.none { it > 1 }) return this
    val reserved = kinds
        .mapNotNull { (section, kindLabel) -> (kindLabel as? KindLabel.Numbered)?.let { section.type.numberingKey to it.number } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, numbers) -> numbers.toSet() }
    val counters = mutableMapOf<String, Int>()
    var lastChorusNumber: Int? = null
    return map { block ->
        val kindLabel = (block as? ChordProBlock.Section)?.takeIf { it.isOfAKind() }?.kindLabel(labels)
        when {
            block is ChordProBlock.Section && kindLabel != null && block.isNumbered(kindLabel) -> {
                val key = block.type.numberingKey
                val numbered = if (counts.getOrElse(key) { 0 } > 1) {
                    val keyReserved = reserved[key].orEmpty()
                    var number = counters.getOrElse(key) { 0 } + 1
                    while (number in keyReserved) number++
                    counters[key] = number
                    block.copy(number = number)
                } else {
                    block
                }
                if (block.type == SectionType.Chorus) lastChorusNumber = numbered.number
                numbered
            }

            block is ChordProBlock.Section -> {
                if (kindLabel is KindLabel.Numbered) {
                    val key = block.type.numberingKey
                    counters[key] = maxOf(counters.getOrElse(key) { 0 }, kindLabel.number)
                }
                if (block.type == SectionType.Chorus && !block.isContinuation) lastChorusNumber = null
                block
            }

            block is ChordProBlock.ChorusRecall && block.label == null && lastChorusNumber != null -> {
                val sectionIndex = block.blocks.indexOfFirst { it is ChordProBlock.Section }
                val first = block.blocks.getOrNull(sectionIndex) as? ChordProBlock.Section
                if (first != null && first.number == null && (first.label == null || first.kindLabel(labels) == KindLabel.Bare)) {
                    block.copy(blocks = block.blocks.mapIndexed { index, piece -> if (index == sectionIndex) first.copy(number = lastChorusNumber) else piece })
                } else {
                    block
                }
            }

            else -> block
        }
    }
}

/** What a section's label says about its kind, see [withNumberedSections]. */
private sealed interface KindLabel {

    /** No label, or one that is not the kind's name. */
    data object None : KindLabel

    /** The kind's name alone ("Verse"). */
    data object Bare : KindLabel

    /** The kind's name and a number ("Verse 2"), which the section reserves. */
    data class Numbered(val number: Int) : KindLabel
}

/** Whether the section can be one of a kind at all: a paragraph is not a kind, and a continuation is the section before it. */
private fun ChordProBlock.Section.isOfAKind() = !isContinuation && type != SectionType.Paragraph

/** Whether the section is headed by its kind's name, which numbering can then add to. */
private fun ChordProBlock.Section.isNumbered(kindLabel: KindLabel) = label == null || kindLabel == KindLabel.Bare

/**
 * Whether the label is the kind's name, with a number after it or without. The number is capped at four digits, so that
 * a label such as "Verse 99999999999" is simply a label rather than a number nothing can hold.
 */
private fun ChordProBlock.Section.kindLabel(labels: DefaultSectionLabels): KindLabel {
    val match = label?.trim()?.let(KIND_LABEL::matchEntire) ?: return KindLabel.None
    if (match.groupValues[1].lowercase() !in type.kindNames(labels)) return KindLabel.None
    return match.groups[2]?.value?.toIntOrNull()?.let(KindLabel::Numbered) ?: KindLabel.Bare
}

private val KIND_LABEL = Regex("""^(.+?)(?:\s+(\d{1,4}))?$""")

/**
 * The names a section of the kind can be labelled with and still be one of it: the heading the app gives it, and its
 * ChordPro name.
 */
private fun SectionType.kindNames(labels: DefaultSectionLabels) = when (this) {
    SectionType.Verse -> setOf(labels.verse, "verse")
    SectionType.Chorus -> setOf(labels.chorus, "chorus")
    SectionType.Bridge -> setOf(labels.bridge, "bridge")
    is SectionType.Custom -> setOf(labels.labelOf(this), name, name.replace('_', ' '))
    SectionType.Paragraph -> emptySet()
}.mapTo(mutableSetOf()) { it.lowercase() }

/** One kind of section: the names of the custom ones are compared the way [DefaultSectionLabels.labelOf] reads them. */
private val SectionType.numberingKey
    get() = when (this) {
        is SectionType.Custom -> name.lowercase()
        else -> toString()
    }

/** [this] followed by [number] where there is one. */
internal fun String.withNumber(number: Int?) = if (number == null) this else "$this $number"
