/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.scales

import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.model.ChordProSong

/**
 * What a song's Scales section suggests: one home answer and at most [MAX_MOMENTS] moments worth changing scale for,
 * however long or complex the song, since a player improvising over it reaches for a few choices, not one per chord.
 *
 * The home is the group of [SongScaleGroups] that plays the most chords, at least half of them, groups offering the same
 * first scale on the same root counted as one. A moment is one of:
 *
 * - **Another key**: any other group whose first scale plays other notes than the home's, a modulated solo or a bridge
 *   in another key, shown as a group of its own; a chorus in the relative minor needs no change of scale.
 * - **Another centre**: a part of a group that hardly plays the group's root (at most one chord in [MAX_ROOT_SHARE]),
 *   never cadences into it, and leans on a root of its own, a bridge on the IV or a chorus on the vi, offered as the scale on that centre
 *   holding most of its chords, the pentatonic first.
 * - **A chord outside its group's scales**: an exception of the group, a secondary dominant or a borrowed ♭VII.
 * - **A dominant seventh whose third the group's first scale lacks**, off the group's root: the V7 of a blues over the
 *   minor pentatonic, whose leading tone it leaves out. Following that change is what makes a solo over a blues
 *   speak, and the textbook scale of the chord (Mixolydian) is offered over it. The chord on the root is what the home
 *   scale was chosen for, so it is never one, and a rock riff's triads keep the minor pentatonic throughout by design.
 *
 * A section is one moment, and so is a chord wherever it is played, together with the chords whose scales are the same
 * notes (a V and its V7; a ii–V of another key, named by that key). Moments are ranked by how much of the song they last ([Moment]),
 * a key or a chord outside the scales weighing double a centre or a dominant the scales hold, and nothing is suggested
 * to fill the budget.
 */
public object SongScaleSuggestions {

    /**
     * The groups to show for [song], in the standard notation, empty where [SongScaleGroups] finds nothing. Where a group
     * plays at least half of the song's chords, it is the home ([ScaleGroup.isHome]), shown first without the parts shown
     * on a centre of their own, then the other keys and centres among the moments in the order they are played. Where
     * none does, the song is in several keys with no home, and every group is shown, in play order. Each group holds the
     * moments over one of its chords as its [ScaleGroup.exceptions], in play order, the first group holding those of a
     * group not shown.
     */
    public fun of(song: ChordProSong): List<ScaleGroup> {
        val groups = sameAnswersJoined(SongScaleGroups.playedGroupsOf(song), song)
        if (groups.isEmpty()) return emptyList()
        val totals = Totals(groups)
        val home = groups.maxBy { it.chords.size }.takeIf { it.chords.size * 2 >= totals.chords }
        val candidates = (home?.let { sectionMomentsOf(groups, it) }.orEmpty() + chordMomentsOf(groups, totals))
            .filter { it.countsIn(totals) }
            .sortedWith(compareByDescending<Moment> { it.weight * it.shareOf(totals) }.thenByDescending { it.count })
        return rowsOf(song, groups, home, chosenOf(candidates, home, totals.parts))
    }

    /**
     * [groups] with those offering the same first scale on the same root joined into one, at the place of the first of
     * them, its blocks in [song]'s order. Groups stay apart where their chords cannot share a fit, a rock riff's verse
     * and a chorus of power chords and triads, yet a viewer would show them as one row twice, and two rows saying the
     * same never help; joined, they are also what decides whether the song has a home.
     */
    private fun sameAnswersJoined(groups: List<PlayedGroup>, song: ChordProSong): List<PlayedGroup> =
        groups.groupBy { it.rootPitchClass to it.group.scales.firstOrNull()?.id }.values.map { same ->
            if (same.size == 1) return@map same.single()
            val first = same.first()
            PlayedGroup(
                group = first.group.copy(
                    blocks = same.flatMap { it.group.blocks }.sortedBy { block -> song.indexOf(block) },
                    exceptions = same.flatMap { it.group.exceptions }.distinctBy { it.chordName },
                ),
                rootPitchClass = first.rootPitchClass,
                names = same.flatMap { it.names },
                rootNames = same.flatMap { it.rootNames },
                chords = same.flatMap { it.chords },
                parts = same.flatMap { it.parts },
            )
        }

    /**
     * The other keys and centres of a song with a [home], the centres first, so that a part both in another key and on
     * a centre of its own is said on its centre, which tells where it sits; a centre in another key weighs as one.
     */
    private fun sectionMomentsOf(groups: List<PlayedGroup>, home: PlayedGroup): List<SectionMoment> {
        // A group in the home's relative key plays the home scale's notes, so improvising over it needs no change.
        val isOtherKey = { group: PlayedGroup -> group !== home && group.leadNotes != home.leadNotes }
        val centres = groups.flatMap { group -> centreMomentsOf(group, weight = if (isOtherKey(group)) KEY_WEIGHT else CENTRE_WEIGHT) }
        val keys = groups.filter(isOtherKey).map { group ->
            SectionMoment(group, KEY_WEIGHT, count = group.chords.size, row = group.group, notes = group.leadNotes)
        }
        return centres + keys
    }

    /**
     * The parts of [group] heard on a centre of their own, see [SongScaleSuggestions], one moment each centre, offered
     * as the scale on it holding most of their chords (major, natural minor, Dorian, or Mixolydian: a bridge of A7 and A6
     * is A Mixolydian), the pentatonic inside it first. A part whose V→I lands on the group's root is heard at home,
     * however little it plays the root (a chorus resolving F♯→Bm in B minor), so it is none.
     */
    private fun centreMomentsOf(group: PlayedGroup, weight: Int): List<SectionMoment> = group.parts
        .filter { part ->
            part.centre != group.rootPitchClass && part.chords.size >= MIN_CENTRE_CHORDS &&
                part.chords.count { it.root == group.rootPitchClass } * MAX_ROOT_SHARE <= part.chords.size &&
                !part.cadencesInto(group.rootPitchClass)
        }
        .groupBy { it.centre }
        .map { (centre, centred) ->
            val chords = centred.flatMap { it.chords }
            val home = centreHomes.maxBy { home -> chords.count { holdsChord(home, centre, it) } }
            val notes = Scales.pitchClasses(home, centre)
            val pentatonic = (if (MAJOR_THIRD in home.intervals) majorPentatonic else minorPentatonic)
                .takeIf { notes.containsAll(Scales.pitchClasses(it, centre)) }
            SectionMoment(
                group = group,
                weight = weight,
                count = chords.size,
                row = ScaleGroup(blocks = centred.flatMap { it.blocks }, rootName = centred.first().centreName, scales = listOfNotNull(pentatonic, home)),
                notes = notes,
            )
        }

    /**
     * Whether the part cadences into [root]: a chord with a major third a fifth above it followed by a chord on it that
     * is no dominant seventh, the part read as a loop, as `SongScaleGroups.rankedRoots` scores a cadence (a `G → C7` in
     * a bridge on F leads on to the F, not home to C). A part that resolves home that way is heard at home however
     * little it plays the root, so it is no centre of its own.
     */
    private fun PlayedPart.cadencesInto(root: Int) = chords.indices.any { index ->
        val chord = chords[index]
        val next = chords[(index + 1) % chords.size]
        MAJOR_THIRD in chord.intervals && (chord.root - next.root).mod(12) == FIFTH && next.root == root && !next.isDominantSeventh
    }

    /**
     * The chords of [groups] worth a scale of their own, see [SongScaleSuggestions], each once, and those whose scales are
     * one set of notes joined into one moment, named by each in play order, as a player changes scale once for a V and its
     * V7 or a ii–V of another key. The moment is named by the key's own scale where one of them is on the group's root or
     * the notes are a common seven-note scale there (a backdoor ii–V is the home's natural minor), else by the parent
     * major key, else by the first.
     */
    private fun chordMomentsOf(groups: List<PlayedGroup>, totals: Totals): List<ChordMoment> = groups.flatMap(::chordMomentsOf)
        .distinctBy { it.exception.chordName }
        .groupBy { Scales.pitchClasses(it.exception.shownScale, it.exception.root) }
        .entries
        .map { (notes, same) ->
            // A moment's index is that of the chord's first play in its own group, and groups are in play order.
            val played = same.sortedWith(compareBy({ groups.indexOf(it.group) }, { it.first }))
            val first = played.first()
            val (rootName, scales) = namedSet(played, notes)
            val exception = ScaleException(
                chordName = first.exception.chordName,
                rootName = rootName,
                scales = scales,
                otherChordNames = played.drop(1).map { it.exception.chordName },
            )
            first.copy(
                weight = same.maxOf { it.weight },
                count = same.sumOf { it.count },
                plays = totals.parts.filter { part -> part.names.any { it in exception.chordNames } }.sumOf { it.plays },
                exception = exception,
                notes = same.flatMapTo(mutableSetOf()) { it.notes },
            )
        }

    /**
     * The root name and scales to say [played] over, chords whose shown scales are all [notes], in the key of the first's
     * group; see [chordMomentsOf]. A home's own borrowed or Phrygian scale is what a player calls it, and other modes of one major
     * scale are that key.
     */
    private fun namedSet(played: List<ChordMoment>, notes: Set<Int>): Pair<String, List<Scale>> {
        val first = played.first()
        val group = first.group
        if (played.all { it.exception.root == first.exception.root }) return first.exception.rootName to first.exception.scales
        played.firstOrNull { it.exception.root == group.rootPitchClass }?.let { return it.exception.rootName to it.exception.scales }
        centreHomes.firstOrNull { Scales.pitchClasses(it, group.rootPitchClass) == notes }?.let { return group.group.rootName to listOf(it) }
        played.firstOrNull { it.exception.shownScale.let { scale -> scale == majorScale || scale.modeOf?.first == MAJOR_ID } }?.let { moment ->
            val scale = moment.exception.shownScale
            val parentName = if (scale == majorScale) moment.exception.rootName else ScaleSpelling.parentRootName(scale, moment.exception.rootName)
            if (parentName != null) return parentName to listOf(majorScale)
        }
        return first.exception.rootName to first.exception.scales
    }

    /** The chords of [group] worth a scale of their own: its exceptions, then its dominants, their plays yet uncounted. */
    private fun chordMomentsOf(group: PlayedGroup): List<ChordMoment> {
        fun momentOf(exception: ScaleException, weight: Int): ChordMoment {
            val first = group.names.indexOf(exception.chordName)
            return ChordMoment(
                group = group,
                weight = weight,
                count = group.names.count { it == exception.chordName },
                plays = 0,
                exception = exception,
                first = first,
                notes = group.chords[first].notes,
            )
        }
        val outside = group.group.exceptions.map { momentOf(it, OUTSIDE_WEIGHT) }
        val dominants = group.chords.indices
            .filter { index ->
                val chord = group.chords[index]
                chord.isDominantSeventh && chord.root != group.rootPitchClass && (chord.root + MAJOR_THIRD).mod(12) !in group.leadNotes &&
                    group.group.exceptions.none { it.chordName == group.names[index] }
            }
            .distinctBy { group.names[it] }
            .mapNotNull { index ->
                val scales = PlayableScales.over(group.chords[index]).filter { it.intervals.size <= HOME_SCALE_SIZE }.ifEmpty { return@mapNotNull null }
                momentOf(ScaleException(chordName = group.names[index], rootName = group.rootNames[index], scales = scales), DOMINANT_WEIGHT)
            }
        return outside + dominants
    }

    /**
     * The best of [candidates], ranked, at most [MAX_MOMENTS] of them, a section never twice (a part both in another
     * key and on a centre of its own is said once). A chord is played over the scales of the row each part playing it is
     * shown in, a key's or a centre's, else [home]'s, so it is a moment only where one of those leaves it out; the slot
     * one that is not held goes to the next candidate that is. With no home, every group is shown, so that holds of all.
     */
    private fun chosenOf(candidates: List<Moment>, home: PlayedGroup?, parts: List<PlayedPart>): List<Moment> {
        val chosen = mutableListOf<Moment>()
        fun take(isAllowed: (Moment) -> Boolean) = candidates.forEach { if (chosen.size < MAX_MOMENTS && it !in chosen && isAllowed(it)) chosen += it }
        take { moment -> moment !is SectionMoment || chosen.none { it is SectionMoment && it.overlaps(moment) } }
        if (home == null) return chosen
        val sections = chosen.filterIsInstance<SectionMoment>()
        fun PlayedPart.shownNotes() = sections.firstOrNull { it.shows(this) }?.notes ?: home.leadNotes
        fun Moment.isPlayable() = this !is ChordMoment || parts.any { part -> part.names.any { it in exception.chordNames } && !part.shownNotes().containsAll(notes) }
        chosen.retainAll { it.isPlayable() }
        take { it is ChordMoment && it.isPlayable() }
        return chosen
    }

    /**
     * The rows [chosen] makes of [groups]: with a [home], the home without the parts a section moment shows, then those
     * sections in play order; with none, every group. Each holds the chord moments of the group it is the row of (the
     * home's, or a key's, a centre being the row of none), the rest going to the first row, the group's own first.
     */
    private fun rowsOf(song: ChordProSong, groups: List<PlayedGroup>, home: PlayedGroup?, chosen: List<Moment>): List<ScaleGroup> {
        val rows: List<Pair<PlayedGroup?, ScaleGroup>> = if (home == null) {
            groups.map { it to it.group }
        } else {
            val sections = chosen.filterIsInstance<SectionMoment>().sortedBy { song.indexOf(it.row.blocks.first()) }
            val shownBlocks = sections.flatMap { it.row.blocks }
            val homeBlocks = home.group.blocks.filter { block -> shownBlocks.none { it === block } }.ifEmpty { home.group.blocks }
            listOf(home to home.group.copy(blocks = homeBlocks, isHome = true)) + sections.map { it.group.takeIf { _ -> it.isKey } to it.row }
        }
        val chords = chosen.filterIsInstance<ChordMoment>()
        return rows.mapIndexed { index, (owner, row) ->
            val over = chords.filter { chord -> rows.indexOfFirst { it.first === chord.group }.coerceAtLeast(0) == index }
            row.copy(exceptions = over.sortedWith(compareBy({ it.group !== owner }, { it.first })).map { it.exception })
        }
    }

    /** The scale a viewer shows over this chord: the first common one, else the first. */
    private val ScaleException.shownScale get() = scales.firstOrNull { it in Scales.common } ?: scales.first()

    /** The pitch class of the exception's root. */
    private val ScaleException.root get() = ChordProChords.pitchClass(rootName) ?: 0

    /** The chords this exception is played over. */
    private val ScaleException.chordNames get() = listOf(chordName) + otherChordNames

    /** The notes of the first scale of the group, the one a player starts from. */
    private val PlayedGroup.leadNotes get() = group.scales.firstOrNull()?.let { Scales.pitchClasses(it, rootPitchClass) }.orEmpty()

    private fun ChordProSong.indexOf(block: Any) = blocks.indexOfFirst { it === block }

    /** How many chords [groups] play and how many times their parts are played between them. */
    private class Totals(groups: List<PlayedGroup>) {
        val parts = groups.flatMap { it.parts }
        val chords = groups.sumOf { it.chords.size }
        val plays = parts.sumOf { it.plays }
    }

    /**
     * Something worth a change of scale in a [group], weighed by [weight] and how much of the song it lasts, its chords
     * played [count] times, a player having [notes] to play under it.
     */
    private sealed interface Moment {
        val group: PlayedGroup
        val weight: Int
        val count: Int
        val notes: Set<Int>

        /** How much of the song the moment lasts, between 0 and 1. */
        fun shareOf(totals: Totals): Double

        /** Whether the moment lasts long enough to be one. */
        fun countsIn(totals: Totals): Boolean
    }

    /**
     * Another key or centre, shown as [row], the group's own for a key, [notes] those of its seven-note scale. A section
     * is played once more often than not, a bridge above all, so it is weighed by its share of the chords, at least one
     * in [MIN_SECTION_SHARE].
     */
    private data class SectionMoment(
        override val group: PlayedGroup,
        override val weight: Int,
        override val count: Int,
        val row: ScaleGroup,
        override val notes: Set<Int>,
    ) : Moment {
        val isKey get() = row === group.group

        override fun shareOf(totals: Totals) = count.toDouble() / totals.chords

        override fun countsIn(totals: Totals) = count * MIN_SECTION_SHARE >= totals.chords

        /** Whether [part] is among the sections this moment shows. */
        fun shows(part: PlayedPart) = row.blocks.any { it === part.blocks.first() }

        fun overlaps(other: SectionMoment) = row.blocks.any { block -> other.row.blocks.any { it === block } }
    }

    /**
     * A chord, or several one scale serves ([exception]), first played at the index [first] of the group's chords and
     * sounding in [plays] of the song's plays (a section and each of its recalls), [notes] its own. A song's chords say
     * how often each is written but not how long it lasts (a chorus may end on a held ♭VII), so a chord is weighed by
     * its share of the plays, at least one in [MIN_PLAY_SHARE]. It counts only where it is also one chord in
     * [MIN_CHORD_SHARE] or comes back in [MIN_RECURRING_PLAYS] plays: a chord passing once is no moment, but one closing
     * every other verse comes back, however few chords it is.
     */
    private data class ChordMoment(
        override val group: PlayedGroup,
        override val weight: Int,
        override val count: Int,
        val plays: Int,
        val exception: ScaleException,
        val first: Int,
        override val notes: Set<Int>,
    ) : Moment {
        override fun shareOf(totals: Totals) = plays.toDouble() / totals.plays

        override fun countsIn(totals: Totals) =
            plays * MIN_PLAY_SHARE >= totals.plays && (count * MIN_CHORD_SHARE >= totals.chords || plays >= MIN_RECURRING_PLAYS)
    }

    private val centreHomes = listOf("major", "natural_minor", "dorian", "mixolydian").map(Scales::fromId).requireNoNulls()
    private val majorScale = Scales.fromId(MAJOR_ID)!!
    private val majorPentatonic = Scales.fromId("major_pentatonic")!!
    private val minorPentatonic = Scales.fromId("minor_pentatonic")!!
    private const val MAJOR_ID = "major"
    private const val MAX_MOMENTS = 3
    private const val MIN_PLAY_SHARE = 8
    private const val MIN_SECTION_SHARE = 16
    private const val MIN_CHORD_SHARE = 32
    private const val MIN_RECURRING_PLAYS = 2
    private const val MAX_ROOT_SHARE = 8
    private const val MIN_CENTRE_CHORDS = 6
    private const val KEY_WEIGHT = 2
    private const val OUTSIDE_WEIGHT = 2
    private const val CENTRE_WEIGHT = 1
    private const val DOMINANT_WEIGHT = 1
    private const val HOME_SCALE_SIZE = 7
    private const val MAJOR_THIRD = 4
    private const val FIFTH = 7
}
