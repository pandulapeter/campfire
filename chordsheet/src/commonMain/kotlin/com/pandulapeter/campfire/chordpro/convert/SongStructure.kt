/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordPlacement.bracketed
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordPlacement.inline
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordPlacement.isUnaligned
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordPlacement.merge
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.chordRow
import com.pandulapeter.campfire.chordpro.convert.ChordSheetText.headerValue
import com.pandulapeter.campfire.chordpro.convert.ChordSheetText.headingLabel

/**
 * Every decision about a song's body: where its sections open and close (by heading or by look), which heading recalls a
 * chorus, the runs of tablature and grid, where key and tempo changes go, the blank lines between parts, and which
 * wrapped rows join the line above. [SongWriter] writes the result as it stands.
 */
internal object SongStructure {

    /** A whole song: its [header]'s directives, each written once, and the [parts] of its body. */
    data class Song(val header: List<String>, val parts: List<Part>)

    /** A grid's shape as ChordPro writes it, its measures by beats with an optional margin each side (`1+4x2+4`). */
    private val gridShape = Regex("(?:[0-9]+\\+)?[0-9]+[x\u00d7][0-9]+(?:\\+[0-9]+)?", RegexOption.IGNORE_CASE)

    sealed interface Part {
        data class Loose(val lines: List<Line>) : Part

        /** [label] is null only for a chorus no heading names, which is set apart by its look alone. */
        data class Section(val type: String, val label: String?, val lines: List<Line>) : Part
    }

    sealed interface Line {
        data object Blank : Line

        /** A lyric with its chords already set in, `[G]Hello`. */
        data class Lyric(val text: String) : Line

        /** A row of chords no lyric is written under, each bracketed. */
        data class Chords(val text: String) : Line
        data class Comment(val text: String, val isBoxed: Boolean) : Line

        /** A line of a tab or a grid, kept as it stands. */
        data class Raw(val text: String) : Line

        /** [lines] are [Raw] rows, and the comments over a staff and the changes that fall inside the run. */
        data class TabRun(val label: String?, val lines: List<Line>) : Line

        /** [lines] are [Raw] rows, and the changes that fall inside the run; [shape] is the one printed over it (`4x4`). */
        data class GridRun(val shape: String?, val lines: List<Line>) : Line
        data object Recall : Line

        /** A key, tempo, or time change, as its directive. */
        data class Change(val directive: String) : Line
    }

    /**
     * The body of the song in [lines], each line of the kind in [kinds]; lines at [omitted] went into the header, and
     * [changes] are the directives a line at their index changes the song with.
     */
    fun of(
        lines: List<Rendered>,
        features: List<LineFeatures>,
        kinds: List<LineKind>,
        candidates: List<List<Token>?>,
        omitted: Set<Int>,
        changes: Map<Int, List<String>>,
        convertParentheses: Boolean,
    ): List<Part> = with(Builder()) {
        fun continues(at: Int) = features[at].isWrapped
        fun follow(at: Int) { if (features[at].isSung) features[at].look?.let { follow(it, lines[at].text) } }
        val unlabelled = ChordSheetLook.unlabelled(
            // A line with no word (a tab's string or a stray accidental the reading took for a lyric) is not sung.
            looks = lines.indices.map { at ->
                if (kinds[at] != LineKind.LYRIC || at in omitted || !features[at].isSung) null else features[at].look
            },
            breaks = kinds.map { it == LineKind.BLANK || it == LineKind.SECTION || it == LineKind.COMMENT || it == LineKind.TAB || it == LineKind.GRID },
            rows = kinds.map { it == LineKind.CHORD },
        )
        // A verse's number names its stanza alone, so a chorus set apart by its look ends it.
        fun openUnlabelled(at: Int) { if (at in unlabelled) { closeNumbered(); openChorus() } }
        val verseNumbers = ChordSheetVerseNumbers.of(lines, kinds)
        val unopenedNumbers = verseNumbers.toMutableMap()
        fun lyricAt(at: Int) = if (at in verseNumbers) ChordSheetVerseNumbers.withoutNumber(lines[at]) else lines[at]
        var index = 0
        while (index < lines.size) {
            changes[index]?.let { pendingChanges += it }
            if (index in omitted) { index++; continue }
            val line = lines[index]
            fun nextContent(after: Int) = ((after + 1)..lines.lastIndex).firstOrNull { kinds[it] != LineKind.BLANK && kinds[it] != LineKind.COMMENT && it !in omitted }
            // A comment over a staff is the tab's own, as a label over it is the tab's.
            if (kinds[index] != LineKind.TAB && !(kinds[index] == LineKind.COMMENT && nextContent(index)?.let { kinds[it] } == LineKind.TAB)) closeTab()
            if (kinds[index] != LineKind.GRID) closeGrid()
            // A change before a heading belongs to the section it heads rather than to the end of the one before.
            if (kinds[index] != LineKind.SECTION && kinds[index] != LineKind.BLANK) flushChanges()
            // A grid's shape printed over it, as ChordPro writes it in the grid's own directive, opens no section.
            if (kinds[index] == LineKind.SECTION && gridShape.matches(headingLabel(line.text)) && nextContent(index)?.let { kinds[it] } == LineKind.GRID) {
                pendingGridShape = headingLabel(line.text)
                index++
                continue
            }
            (unopenedNumbers.remove(index) ?: unopenedNumbers.takeIf { kinds[index] == LineKind.CHORD }?.remove(index + 1))?.let { number ->
                closeSection()
                openNumbered(number)
            }
            when (kinds[index]) {
                LineKind.COMMENT -> {
                    var end = index
                    while (end < lines.lastIndex && kinds[end + 1] == LineKind.COMMENT && end + 1 !in omitted) end++
                    // A comment the next section (or the song's end) follows stands after the section it is printed under.
                    if (!isInTab && nextContent(end)?.let { kinds[it] == LineKind.SECTION } != false) {
                        closeSection()
                        if (endsInText()) add(Line.Blank)
                    }
                    add(Line.Comment(headerValue((index..end).joinToString(" ") { lines[it].text.trim() }), isBoxed = false))
                    index = end
                }
                LineKind.GRID -> { if (!isInGrid) openGrid(); add(Line.Raw(line.text.trimEnd())) }
                LineKind.SECTION -> {
                    closeSection()
                    flushChanges()
                    val label = headerValue(headingLabel(line.text))
                    val type = when {
                        line.isMarginLabel -> ChordSheetMarginLabels.type(line.text)
                        features[index].isLabel -> ChordSheetHeadings.sectionType(label)
                        else -> features[index].heading?.type
                    }
                    val next = ((index + 1)..lines.lastIndex).firstOrNull { kinds[it] != LineKind.BLANK }
                    // Under a chorus heading, lines neither set the way the song set its chorus apart nor singing its words
                    // are sung after it.
                    val lyric = ((index + 1)..lines.lastIndex).takeWhile { kinds[it] != LineKind.SECTION }.firstOrNull { kinds[it] == LineKind.LYRIC && it !in omitted }
                    val isRecall = type != null && lyric != null && sections.isRecall(type, features[lyric].look, lines[lyric].text)
                    if (type == "chorus" && (next == null || kinds[next] == LineKind.SECTION || isRecall)) add(Line.Recall)
                    else if (features[index].isLabel && nextContent(index)?.let { kinds[it] } == LineKind.TAB) openTab(label)
                    else if (type != null) open(type, label)
                    else {
                        // A heading the parser does not take for a section ends the lyrics before it only after a blank
                        // line; without one it would be a comment inside them, and what follows their continuation.
                        if (endsInText()) add(Line.Blank)
                        add(Line.Comment(label, isBoxed = false))
                    }
                }
                LineKind.TAB -> { if (!isInTab) openTab(label = null); add(Line.Raw(line.text)) }
                LineKind.CHORD -> {
                    openUnlabelled(index + 1)
                    if (!line.isFromLabel && kinds.getOrNull(index + 1) == LineKind.LYRIC && index + 1 !in omitted && !isUnaligned(line, candidates[index]!!, lyricAt(index + 1))) {
                        follow(index + 1)
                        addLyric(merge(line, lyricAt(index + 1), convertParentheses), continues(index + 1))
                        index++
                    } else add(Line.Chords(bracketed(candidates[index]!!, chordRow(line))))
                }
                LineKind.LYRIC -> {
                    openUnlabelled(index)
                    follow(index)
                    addLyric(inline(lyricAt(index), convertParentheses), continues(index))
                }
                LineKind.BLANK -> { closeNumbered(); add(Line.Blank) }
                LineKind.METADATA -> Unit
            }
            index++
        }
        closeTab()
        closeGrid()
        closeSection()
        flushChanges()
        built()
    }

    /**
     * The body built so far: the parts closed, the loose lines since the last one, and the section, tab, or grid run open
     * around the next line. Whether a blank line parts what comes next, and whether a wrapped row joins the line above,
     * is read from the last line this would write, so the structure says exactly what the written song shows.
     */
    private class Builder {
        private val parts = mutableListOf<Part>()
        private val loose = mutableListOf<Line>()
        private var section: OpenSection? = null
        private var run: OpenRun? = null
        val pendingChanges = mutableListOf<String>()
        val sections = SongSections()

        private class OpenSection(val type: String, val label: String?, val lines: MutableList<Line> = mutableListOf())

        private class OpenRun(val isTab: Boolean, val label: String?, val lines: MutableList<Line> = mutableListOf()) {
            fun closed(): Line = if (isTab) Line.TabRun(label, lines.toList()) else Line.GridRun(label, lines.toList())
        }

        val isInTab get() = run?.isTab == true
        val isInGrid get() = run?.isTab == false

        /** The innermost open container, which the next line goes into. */
        private val container get() = run?.lines ?: section?.lines ?: loose

        fun add(line: Line) {
            container += line
        }

        /** The last line the song so far would write, its opening directive where the innermost container is empty. */
        private fun lastWritten(): String? {
            run?.let { return it.lines.lastOrNull()?.let(::lastWritten) ?: SongWriter.write(it.closed()).first() }
            section?.let { return it.lines.lastOrNull()?.let(::lastWritten) ?: SongWriter.write(Part.Section(it.type, it.label, emptyList())).first() }
            return loose.lastOrNull()?.let(::lastWritten) ?: parts.lastOrNull()?.let { SongWriter.write(it).last() }
        }

        private fun lastWritten(line: Line) = SongWriter.write(line).last()

        fun endsInText() = lastWritten()?.isNotBlank() == true

        /** A lyric line, or where it [continues] the one before (a row a narrow column wrapped), the rest of that line. */
        fun addLyric(line: String, continues: Boolean) {
            val previous = lastWritten()
            if (continues && previous != null && previous.isNotBlank() && !previous.startsWith('{')) {
                container[container.lastIndex] = Line.Lyric("$previous ${line.trimStart()}")
            } else add(Line.Lyric(line))
        }

        fun openTab(label: String?) {
            run = OpenRun(isTab = true, label = label)
        }

        /** The shape printed over the next grid, which it opens with. */
        var pendingGridShape: String? = null

        fun openGrid() {
            run = OpenRun(isTab = false, label = pendingGridShape)
            pendingGridShape = null
        }

        fun closeTab() { if (isInTab) closeRun() }
        fun closeGrid() { if (isInGrid) closeRun() }

        private fun closeRun() {
            val closed = run?.closed() ?: return
            run = null
            add(closed)
        }

        /** Whether the open section is a verse its number opened, which names its stanza alone. */
        private var isNumbered = false

        fun openNumbered(number: String) {
            open("verse", number)
            isNumbered = true
        }

        fun closeNumbered() { if (isNumbered) closeSection() }

        fun open(type: String, label: String) {
            openSection(type, label)
            sections.opened()
        }

        /** A chorus no heading names, set apart by its type alone, which a return to plain type ends. */
        fun openChorus() {
            if (section != null || run != null) return
            if (endsInText()) add(Line.Blank)
            openSection("chorus", label = null)
            sections.openedUnlabelled()
        }

        private fun openSection(type: String, label: String?) {
            if (loose.isNotEmpty()) parts += Part.Loose(loose.toList())
            loose.clear()
            section = OpenSection(type, label)
        }

        /**
         * A lyric of [look]: a section set apart from the verses ends where the lyrics return to their look
         * ([SongSections.endsAt]).
         */
        fun follow(look: ChordSheetLook, text: String) {
            if (sections.endsAt(look)) {
                // A section ending mid-song ends with its last stanza, before the blank lines parting it from the next.
                val lines = section!!.lines
                val blanks = lines.takeLastWhile { SongWriter.write(it).singleOrNull()?.isBlank() == true }.size
                repeat(blanks) { lines.removeAt(lines.lastIndex) }
                closeSection(isReturn = true)
                repeat(blanks) { add(Line.Blank) }
            }
            sections.record(look, text, isInSection = section != null)
        }

        fun closeSection(isReturn: Boolean = false) {
            val open = section ?: return
            sections.closed(open.type, isReturn)
            section = null
            isNumbered = false
            parts += Part.Section(open.type, open.label, open.lines.toList())
        }

        fun flushChanges() {
            pendingChanges.forEach { add(Line.Change(it)) }
            pendingChanges.clear()
        }

        fun built(): List<Part> = if (loose.isEmpty()) parts.toList() else parts + Part.Loose(loose.toList())
    }
}
