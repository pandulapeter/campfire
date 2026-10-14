/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.chords

import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProStaffNames.StaffPrefix
import com.pandulapeter.campfire.chordpro.chords.ChordProStaffNames.respelled
import com.pandulapeter.campfire.chordpro.chords.ChordProStaffNames.shiftedBy
import com.pandulapeter.campfire.chordpro.chords.ChordProStaffNames.staffPrefix
import com.pandulapeter.campfire.chordpro.chords.ChordProTabTransposer.SEMITONES_IN_OCTAVE
import com.pandulapeter.campfire.chordpro.edit.ChordProTabWrapper
import com.pandulapeter.campfire.chordpro.model.TabNotice
import com.pandulapeter.campfire.chordpro.model.TabPlayer
import com.pandulapeter.campfire.chordpro.model.TabTuning
import com.pandulapeter.campfire.chordpro.syntax.ChordProTokens
import kotlin.math.abs

/**
 * Moves one run of tablature to the strings of a [TabPlayer], for [ChordProTransposer], so that it sounds what it was
 * written to sound: the strings a staff names (`Eb|`, `e|`) are the tuning it was written in, every one of them is moved
 * by how far it is from the player's string it lands on, and the transposition is added on top. A staff is taken to be
 * written for the instrument [TabPlayer.assumedTuning] names from the strings it names, and one that names no strings
 * is in the standard tuning that gives it. The blank lines of the run arrive as empty lines, which end a system the way
 * any other line between two staves does, and leave as they came.
 *
 * Every staff line is named for the string it lands on, so the run reads as a tab for the player's instrument, and a
 * staff that named nothing is named all the same. One decision is made for the whole run, as the plain transposition
 * makes it: a run that would take a fret off the neck is moved by whole octaves, and one that no octave keeps on it, or
 * whose strings cannot be lined up with the player's, is shown as it is written, with its own names, and only moved by
 * the transposition. Either is said in a [TabNotice]; a run moved string by string and still on the neck says nothing.
 */
internal object ChordProTabRetuner {

    /** A rewritten run: its lines in order, null for a staff line of a string the player does not have, and the notice. */
    class Run(val lines: List<String?>, val notice: TabNotice?)

    fun retune(lines: List<String>, semitones: Int, rename: (String) -> String, selector: String?, player: TabPlayer): Run {
        val isStaffLine = lines.map(ChordProTokens::isStaffLine)
        if (isStaffLine.none { it }) return Run(ChordProTabTransposer.transpose(lines, semitones, rename), null)
        // A negated selector says what the tab is not for, which tells nothing about what it is for.
        val instrumentHint = selector?.takeUnless { it.endsWith(NEGATION) }
        val systems = ChordProTabWrapper.systems(lines, isStaffLine).map { range -> readSystem(lines, isStaffLine, range, instrumentHint, player) }
        val target = player.tuning
        if (target != null) {
            val moved = systems.mapNotNull { system -> system.movedTo(target, semitones) }
            if (moved.size == systems.size) {
                octaveOffset(moved)?.let { offset ->
                    val instrument = moved.firstNotNullOfOrNull { it.otherInstrument }
                    // Moved onto another instrument a tab keeps its notes and not their octaves, so which octave it
                    // lands in is no decision of its own to tell about.
                    val octaves = if (instrument == null) offset / SEMITONES_IN_OCTAVE else 0
                    return Run(
                        lines = rewrite(lines, moved, offset, rename),
                        notice = if (instrument != null || octaves != 0) TabNotice(TabNotice.Kind.MOVED, instrument, octaves) else null,
                    )
                }
            }
        }
        // A staff of a number of strings no instrument has is not a tab of anybody's: it is left as it is, unnamed.
        val notMoved = if (target == null || systems.all { it.isUnknown }) null else TabNotice(TabNotice.Kind.NOT_MOVED, otherInstrument(systems, target))
        val asWritten = systems.map { system -> system.asWritten(semitones) }
        val offset = octaveOffset(asWritten) ?: return Run(lines, notMoved)
        return Run(
            lines = rewrite(lines, asWritten, offset, rename),
            notice = notMoved?.copy(octaves = offset / SEMITONES_IN_OCTAVE)
                ?: TabNotice(TabNotice.Kind.MOVED, octaves = offset / SEMITONES_IN_OCTAVE).takeIf { offset != 0 },
        )
    }

    /** The instrument the run is written for where it is not the one it is read on, or null. */
    private fun otherInstrument(systems: List<StaffSystem>, target: TabTuning) =
        systems.firstNotNullOfOrNull { system -> system.tuning?.instrument?.takeIf { it != target.instrument } }

    /**
     * The instrument the first staff of a run is written for, as [TabPlayer.assumedTuning] names it from the strings the
     * staff names and the selector of its environment, or null where no instrument has as many strings: what a notice
     * about a run that is not shown at all names it by.
     */
    fun instrumentOf(lines: List<String>, selector: String?, player: TabPlayer): String? {
        val isStaffLine = lines.map(ChordProTokens::isStaffLine)
        val range = ChordProTabWrapper.systems(lines, isStaffLine).firstOrNull { range -> range.any { isStaffLine[it] } } ?: return null
        val staff = range.filter { isStaffLine[it] }
        return assumedTuning(staff.map { staffPrefix(lines[it]) }, selector?.takeUnless { it.endsWith(NEGATION) }, player)?.instrument
    }

    /** The standard tuning [player] takes a staff opened by [prefixes] to be written in, where an instrument has as many strings. */
    private fun assumedTuning(prefixes: List<StaffPrefix>, instrumentHint: String?, player: TabPlayer) =
        player.assumedTuning(prefixes.map { it.note }.asReversed(), instrumentHint)?.takeIf { it.strings.size == prefixes.size }

    private fun readSystem(lines: List<String>, isStaffLine: List<Boolean>, range: IntRange, instrumentHint: String?, player: TabPlayer): StaffSystem {
        val staff = range.filter { isStaffLine[it] }
        val prefixes = staff.map { staffPrefix(lines[it]) }
        val standard = assumedTuning(prefixes, instrumentHint, player)
        val notes = prefixes.map { it.note }
        val tuning = standard?.let {
            val standardFromTop = standard.strings.asReversed()
            val named = notes.filterNotNull()
            when {
                named.isEmpty() -> standard
                named.size == notes.size -> standard.copy(strings = named.mapIndexed { line, note -> nearest(note, standardFromTop[line]) }.asReversed())
                notes.withIndex().all { (line, note) -> note == null || note == standardFromTop[line].mod(SEMITONES_IN_OCTAVE) } -> standard
                else -> null
            }
        }
        return StaffSystem(
            lines = lines,
            range = range,
            staff = staff,
            prefixes = prefixes,
            tuning = tuning,
            isUnknown = standard == null,
            stringName = player.stringName,
            notation = player.notation,
        )
    }

    /** The note of [pitchClass] closest to [reference], the lower one where two are as close: a string tuned down. */
    private fun nearest(pitchClass: Int, reference: Int): Int {
        val below = reference - (reference - pitchClass).mod(SEMITONES_IN_OCTAVE)
        return if (reference - below <= below + SEMITONES_IN_OCTAVE - reference) below else below + SEMITONES_IN_OCTAVE
    }

    /**
     * One system of a run: its staff lines from the top down and what opens each of them, and the tuning it is in — the
     * notes its staff names, in the octaves of the standard tuning of the instrument it is for, or that standard tuning
     * itself where it names none. Null where no instrument has as many strings ([isUnknown]), or where the strings it
     * names disagree with that standard tuning about the ones it leaves unnamed, since nothing says what those are then.
     */
    private class StaffSystem(
        val lines: List<String>,
        val range: IntRange,
        val staff: List<Int>,
        val prefixes: List<StaffPrefix>,
        val tuning: TabTuning?,
        val isUnknown: Boolean,
        val stringName: (Int) -> String,
        val notation: ChordNotation,
    ) {

        fun fretsOf(line: Int) = ChordProTabTransposer.fretNumbers(lines[staff[line]].substring(prefixes[line].bodyStart))

        /**
         * The system moved to [target] and by [semitones], or null where its strings cannot be lined up with the
         * target's. On the instrument it is written for every string is moved by exactly how far it is from the
         * player's. On another one only the notes can be kept and not the octaves, so every string it plays has to land
         * on one of the target's, moved by nearly the same amount as the others, or the shapes it is played in would
         * fall apart; of the ways it lines up, the one that moves it least.
         */
        fun movedTo(target: TabTuning, semitones: Int): Plan? {
            val tuning = tuning ?: return null
            val fromTop = tuning.strings.asReversed()
            val targetFromTop = target.strings.asReversed()
            val isSameInstrument = tuning.instrument == target.instrument && fromTop.size == targetFromTop.size
            val alignment = if (isSameInstrument) {
                Alignment(offset = 0, shifts = fromTop.indices.map { line -> fromTop[line] - targetFromTop[line] })
            } else {
                val played = staff.indices.filter { line -> fretsOf(line).isNotEmpty() }.ifEmpty { staff.indices.toList() }
                (1 - fromTop.size until targetFromTop.size)
                    .filter { offset -> played.all { line -> line + offset in targetFromTop.indices } }
                    .map { offset ->
                        Alignment(
                            offset = offset,
                            shifts = fromTop.indices.map { line -> targetFromTop.getOrNull(line + offset)?.let { pitchClassShift(fromTop[line], it) } ?: 0 },
                        )
                    }
                    .filter { alignment -> alignment.spreadOf(played) <= MAX_SHIFT_SPREAD }
                    .minWithOrNull(compareBy({ it.spreadOf(played) }, { alignment -> played.sumOf { abs(alignment.shifts[it]) } }))
                    ?: return null
            }
            val notes = staff.indices.map { line -> targetFromTop.getOrNull(line + alignment.offset) }
            // Strings of another instrument that land on the very notes they were tuned to (the six of a guitar on a
            // seven-string one) were moved by no decision of anybody's, which is nothing to tell about.
            val isExact = staff.indices.all { line -> notes[line]?.let { note -> fromTop[line] - note == alignment.shifts[line] } ?: true }
            return Plan(
                system = this,
                shifts = alignment.shifts.map { it + semitones },
                names = namesOf(notes),
                dropped = staff.filterIndexed { line, _ -> notes[line] == null }.toSet(),
                otherInstrument = tuning.instrument.takeUnless { isSameInstrument || isExact },
            )
        }

        /**
         * The system moved by [semitones] alone: the assumed names written where it names no strings, and where it names
         * them all, its own names in the reader's notation, which only rewrites the staff where one of them reads
         * differently there.
         */
        fun asWritten(semitones: Int) = Plan(
            system = this,
            shifts = staff.map { semitones },
            names = tuning?.let { tuning ->
                if (prefixes.any { it.note == null }) {
                    namesOf(tuning.strings.asReversed())
                } else {
                    prefixes.map { prefix -> prefix.name?.let { respelled(it, from = ChordNotation.STANDARD, to = notation) } }.takeIf { names -> names != prefixes.map { it.name } }
                }
            },
            dropped = emptySet(),
            otherInstrument = null,
        )

        private fun namesOf(notes: List<Int?>) = stringNamesOf(notes, stringName)
    }

    /**
     * The names of the strings tuned to [notes], from the top line of a staff down (null for one that is left out),
     * as [stringName] names them, the highest of the strings that share a name in lowercase, as `e B G D A E` tells the
     * two Es apart and a banjo's `d B G D g` its two Ds and two Gs.
     */
    fun stringNamesOf(notes: List<Int?>, stringName: (Int) -> String): List<String?> {
        val names = notes.map { note -> note?.let(stringName) }
        val highestOfShared = notes.withIndex()
            .mapNotNull { (line, note) -> names[line]?.let { name -> Triple(line, name.lowercase(), note) } }
            .groupBy { (_, name, _) -> name }
            .values
            .filter { it.size > 1 }
            .mapNotNull { strings -> strings.maxByOrNull { (_, _, note) -> note ?: Int.MIN_VALUE }?.first }
            .toSet()
        return names.mapIndexed { line, name -> if (line in highestOfShared) name?.lowercase() else name }
    }

    /** The shift from a string to another as a pitch class, from a tritone down to a fourth up. */
    private fun pitchClassShift(from: Int, to: Int) = (from - to).mod(SEMITONES_IN_OCTAVE).let { if (it >= SEMITONES_IN_OCTAVE / 2) it - SEMITONES_IN_OCTAVE else it }

    /** The strings of a system lined up with the target's: line `n` of the staff on its line `n + offset`, moved by its shift. */
    private class Alignment(val offset: Int, val shifts: List<Int>) {

        fun spreadOf(lines: List<Int>) = lines.maxOf { shifts[it] } - lines.minOf { shifts[it] }
    }

    /**
     * What is done to the staff lines of one system: each moved by its own number of semitones, renamed from the top
     * line down where [names] is given (and kept where it is null), and the lines of [dropped] left out. [otherInstrument]
     * is the instrument it was written for where moving it to another one's strings took a decision.
     */
    private class Plan(val system: StaffSystem, val shifts: List<Int>, val names: List<String?>?, val dropped: Set<Int>, val otherInstrument: String?)

    /**
     * How many semitones of whole octaves every staff line is moved by on top of its own shift so that every fret of the
     * run stays on the neck, the one closest to not moving at all, or null where none does.
     */
    private fun octaveOffset(plans: List<Plan>): Int? {
        var lowest = Int.MIN_VALUE
        var highest = Int.MAX_VALUE
        plans.forEach { plan ->
            plan.system.staff.forEachIndexed { line, index ->
                if (index in plan.dropped) return@forEachIndexed
                plan.system.fretsOf(line).forEach { fret ->
                    val moved = fret + plan.shifts[line]
                    lowest = maxOf(lowest, -moved.floorDiv(SEMITONES_IN_OCTAVE))
                    highest = minOf(highest, (ChordVoicings.MAX_FRET - moved).floorDiv(SEMITONES_IN_OCTAVE))
                }
            }
        }
        if (lowest == Int.MIN_VALUE) return 0
        if (lowest > highest) return null
        return SEMITONES_IN_OCTAVE * 0.coerceIn(lowest, highest)
    }

    private fun rewrite(lines: List<String>, plans: List<Plan>, offset: Int, rename: (String) -> String): List<String?> {
        val rewritten = lines.toMutableList<String?>()
        plans.forEach { plan ->
            val system = plan.system
            val names = plan.names
            // Where the names are written, the music of every staff line starts in the column after the widest name and
            // its bar, and the lines above the staff move with it, so that a chord stays over the note it is written over.
            val nameWidth = names?.maxOf { it?.length ?: 0 } ?: 0
            val columnShift = if (names == null) 0 else nameWidth + 1 - (system.prefixes.firstOrNull()?.bodyStart ?: 0)
            system.range.forEach { index ->
                val line = system.staff.indexOf(index)
                rewritten[index] = when {
                    index in plan.dropped -> null
                    line < 0 -> ChordProTabTransposer.rewriteChordLine(lines[index], rename).shiftedBy(columnShift)
                    else -> {
                        val bodyStart = system.prefixes[line].bodyStart
                        val body = ChordProTabTransposer.transposeStaffLine(lines[index].substring(bodyStart), plan.shifts[line] + offset)
                        if (names == null) lines[index].substring(0, bodyStart) + body else names[line].orEmpty().padStart(nameWidth) + BAR + body
                    }
                }
            }
        }
        return rewritten
    }

    /**
     * How far apart the moves of the strings of one tab may be where it is moved onto another instrument: two semitones,
     * the most a drop tuning moves one string by. Further apart, the tab's shapes no longer hold.
     */
    private const val MAX_SHIFT_SPREAD = 2
    private const val BAR = '|'
    private const val NEGATION = "!"
}
