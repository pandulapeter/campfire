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
import com.pandulapeter.campfire.chordpro.model.Chord
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.GridToken
import com.pandulapeter.campfire.chordpro.syntax.ChordProTokens
import com.pandulapeter.campfire.chordpro.tabs.ChordProTabTransposer

/**
 * The scales a song can be played over, part by part: a song that one scale fits whole is one group, and a song that
 * modulates for its solo, or whose chorus leaves the verse's scale, is several, each a set of its sections with the
 * scales that fit them all.
 *
 * Groups are decided by the scales of at most seven notes and the blues (see [fitOf]), so a song splits where its parts
 * need different ones. Sections are grouped in the order the song plays them, each joining the group whose chords it can
 * share a scale with: first one holding a section of its own kind (a second verse goes with the first), then the group
 * the section before it is in, then any other, so that a verse after the solo goes back to the verses rather than starting a group of its
 * own. A section that fits no group starts one, and one whose chords no scale holds is left out. The root of a group is
 * the key in force at its first section (the song's `{key}`, or the key the last `{transpose}` before it names) where
 * that key's scale holds its chords, else the root its chords are heard in, scored by how often, where, and after what
 * each is played (see [fitOf]).
 *
 * The song is read as it stands: every chord as written, and a `{transpose}` only for the key it names, never as a move
 * to make. The transposer has already moved the chords after one, and renamed its key, by the time a page shows them;
 * a song it has not been through reads as written, its chords and its `{transpose}` keys agreeing just the same.
 */
public object SongScaleGroups {

    /** The groups of [song], which is in the standard notation, in the order their first sections are played. */
    public fun of(song: ChordProSong): List<ScaleGroup> {
        val parts = partsOf(song)
        if (parts.isEmpty()) return emptyList()
        fitOf(parts, isStrict = true)?.let { return listOf(ScaleGroup(parts.flatMap { it.sections }, it.root.name, it.scales)) }
        val groups = mutableListOf<Group>()
        parts.forEach { part ->
            val isStrict = fitOf(listOf(part), isStrict = true) != null
            val candidates = groups.filter { it.isStrict == isStrict }.sortedBy { group ->
                when {
                    group.parts.any { it.kind == part.kind } -> 0
                    group === groups.last() -> 1
                    else -> 2
                }
            }
            val joined = candidates.firstNotNullOfOrNull { group -> fitOf(group.parts + part, isStrict)?.let { group to it } }
            if (joined != null) {
                joined.first.parts += part
                joined.first.fit = joined.second
            } else {
                fitOf(listOf(part), isStrict)?.let { groups += Group(mutableListOf(part), it, isStrict) }
            }
        }
        return groups.map { ScaleGroup(it.parts.flatMap { part -> part.sections }, it.fit.root.name, it.fit.scales) }
    }

    /**
     * The scales to play over every chord of [parts] ([PlayableScales]) on the root their chords are heard in, or null
     * where none are suggested. A strict fit asks for a scale of at most seven notes holding them, or a dominant blues:
     * the eight-note scales (bebop, flamenco, the diminished ones) hold a passing tone that lets a verse in major and a
     * chorus in Mixolydian share one, which is no answer to which scale each is in. Either way every scale suggested is
     * offered, the eight-note ones among them, the scale that decided the root first.
     *
     * The root is, in this order: the key in force at the first part where its own scale holds the chords (major, or
     * natural then harmonic minor for a minor key); the best scored root ([rankedRoots]) a major, natural minor, or
     * harmonic minor scale holds the chords on, unless the first chord's root or the key's ranks above it and Mixolydian
     * or Dorian holds them there; else the best scored root with a fit, a non-strict one only on the very best. Lydian,
     * Phrygian, and Locrian are never chosen as a centre, since a chart almost never sits on them, and a mode is taken
     * only where the part starts on it or the key names it, which every modal song a player calls modal does.
     */
    private fun fitOf(parts: List<Part>, isStrict: Boolean): Fit? {
        val chords = parts.flatMap { it.chords }
        val notes = chords.flatMapTo(mutableSetOf()) { chord -> chord.intervals.map { (chord.root + it).mod(12) } }
        val key = parts.first().key
        fun holding(root: Root, ids: List<String>) = ids.firstNotNullOfOrNull { id ->
            Scales.fromId(id)?.takeIf { Scales.pitchClasses(it, root.pitchClass).containsAll(notes) }
        }
        fun fit(root: Root, scale: Scale): Fit? = PlayableScales.suggestionFor(root.pitchClass, chords)?.let { suggestion ->
            Fit(root, listOf(scale) + (suggestion.scales - scale))
        }
        key?.let { holding(it.root, if (it.isMinor) minorKeyScaleIds else majorKeyScaleIds) }?.let { return fit(key.root, it) }
        val ranked = rankedRoots(parts)
        val homeIndex = ranked.indexOfFirst { holding(it, homeScaleIds) != null }
        val modeCandidates = listOfNotNull(parts.first().roots.firstOrNull()?.pitchClass, key?.root?.pitchClass)
        val modeRoots = (ranked.filter { it.pitchClass in modeCandidates } + listOfNotNull(key?.root))
            .distinctBy { it.pitchClass }
            .filter { homeIndex == -1 || ranked.indexOfFirst { root -> root.pitchClass == it.pitchClass }.let { index -> index in 0 until homeIndex } }
        modeRoots.firstNotNullOfOrNull { root -> holding(root, modeScaleIds)?.let { fit(root, it) } }?.let { return it }
        if (homeIndex != -1) ranked[homeIndex].let { root -> return fit(root, holding(root, homeScaleIds)!!) }
        ranked.firstNotNullOfOrNull { root ->
            PlayableScales.suggestionFor(root.pitchClass, chords)?.takeIf { it.isFit }?.let { Fit(root, it.scales) }
        }?.let { return it }
        if (isStrict) return null
        return ranked.firstOrNull()?.let { root -> PlayableScales.suggestionFor(root.pitchClass, chords)?.let { Fit(root, it.scales) } }
    }

    /**
     * Every root [parts] play, the one they are most likely heard in first. A root scores one for each chord on it but a
     * dominant seventh (a chord that leaves, rarely a tonic), one for the first chord and one for the last, and two for
     * every cadence into it, a chord with a major third a fifth above it followed by a chord on it, each part read as a
     * loop too, since a section is repeated. Ties go to more chords that are not a dominant seventh, then the first
     * chord's root, then the last one's.
     */
    private fun rankedRoots(parts: List<Part>): List<Root> {
        val played = parts.flatMap { it.roots }
        val first = played.first().pitchClass
        val last = played.last().pitchClass
        val scores = mutableMapOf<Int, Int>()
        val counts = mutableMapOf<Int, Int>()
        parts.forEach { part ->
            part.chords.forEachIndexed { index, chord ->
                if (!chord.isDominantSeventh) {
                    scores[chord.root] = (scores[chord.root] ?: 0) + 1
                    counts[chord.root] = (counts[chord.root] ?: 0) + 1
                }
                val next = part.chords[(index + 1) % part.chords.size]
                if (MAJOR_THIRD in chord.intervals && (chord.root - next.root).mod(12) == FIFTH) {
                    scores[next.root] = (scores[next.root] ?: 0) + CADENCE_SCORE
                }
            }
        }
        scores[first] = (scores[first] ?: 0) + 1
        scores[last] = (scores[last] ?: 0) + 1
        return played.distinctBy { it.pitchClass }.sortedWith(
            compareByDescending<Root> { scores[it.pitchClass] ?: 0 }
                .thenByDescending { counts[it.pitchClass] ?: 0 }
                .thenByDescending { it.pitchClass == first }
                .thenByDescending { it.pitchClass == last },
        )
    }

    private val Chord.isDominantSeventh get() = intervals.map { it.mod(12) }.let { MAJOR_THIRD in it && MINOR_SEVENTH in it }

    /**
     * Every section of [song] that plays a chord, its continuations with it, a chorus recall left out since it plays
     * the chorus again, each with the notes of its chords and the roots it plays, and the key in force at it.
     */
    private fun partsOf(song: ChordProSong): List<Part> {
        var key = song.metadata.key?.let(::keyOf)
        val parts = mutableListOf<Part>()
        song.blocks.forEach { block ->
            when (block) {
                is ChordProBlock.Transpose -> key = block.key?.let(::keyOf) ?: key
                is ChordProBlock.Section -> {
                    val chords = block.lines.flatMap { it.chordNames() }.mapNotNull { name ->
                        ChordProChords.parse(name)?.let { chord -> rootOf(name)?.let { it to chord } }
                    }
                    val previous = parts.lastOrNull()?.takeIf { block.isContinuation && it.sections.last().type == block.type }
                    when {
                        previous != null -> {
                            previous.sections += block
                            previous.add(chords)
                        }
                        chords.isNotEmpty() -> parts += Part(mutableListOf(block), key).apply { add(chords) }
                    }
                }
                else -> Unit
            }
        }
        return parts.filter { it.roots.isNotEmpty() }
    }

    /** The chord names one line plays, by what kind of line it is: the walk `ChordProChords.namesIn` takes. */
    private fun ChordProLine.chordNames(): List<String> = when (this) {
        is ChordProLine.Lyrics -> chords.filter { !it.isAnnotation }.map { it.name }
        is ChordProLine.Grid -> tokens.filterIsInstance<GridToken.Chord>().flatMap { ChordProTokens.cellChords(it.name) }
        is ChordProLine.Tab -> ChordProTabTransposer.chordNames(listOf(text))
        ChordProLine.Blank -> emptyList()
    }

    /**
     * The key [text] names, minor where what follows its root starts with a lowercase `m` other than `ma` (`Am`,
     * `A minor`, `A-moll`), so that `Amaj`, `A major`, and `A mayor` stay major.
     */
    private fun keyOf(text: String): Key? = rootOf(text)?.let { root ->
        val mode = text.trim().drop(root.name.length).trimStart('-', ' ')
        Key(root, isMinor = mode.startsWith('m') && mode.getOrNull(1) != 'a')
    }

    /** The root a chord name or a key starts with, spelled as written: `Eb` for `Ebm7` and for `Eb minor`. */
    private fun rootOf(name: String): Root? {
        val trimmed = name.trim()
        val letter = trimmed.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'G' } ?: return null
        val accidental = trimmed.getOrNull(1)?.takeIf { it == '#' || it == 'b' }
        val spelled = letter + (accidental?.toString() ?: "")
        return ChordProChords.pitchClass(spelled)?.let { Root(it, spelled) }
    }

    private class Part(val sections: MutableList<ChordProBlock.Section>, val key: Key?) {
        val chords = mutableListOf<Chord>()
        val roots = mutableListOf<Root>()

        /** What a section is of, so that a second verse finds the first: its type, and its label without a number. */
        val kind get() = sections.first().let { it.type to it.label?.trimEnd { char -> char.isDigit() || char.isWhitespace() }?.lowercase() }

        fun add(played: List<Pair<Root, Chord>>) = played.forEach { (root, chord) ->
            roots += root
            // The bass of a slash chord is left out: it is a voicing, and a passing bass note is often outside the scale.
            chords += Chord(root = chord.root, intervals = chord.intervals)
        }
    }

    private class Group(val parts: MutableList<Part>, var fit: Fit, val isStrict: Boolean)

    private class Fit(val root: Root, val scales: List<Scale>)

    private class Root(val pitchClass: Int, val name: String)

    private class Key(val root: Root, val isMinor: Boolean)

    private const val MAJOR_THIRD = 4
    private const val FIFTH = 7
    private const val MINOR_SEVENTH = 10
    private const val CADENCE_SCORE = 2
    private val majorKeyScaleIds = listOf("major")
    private val minorKeyScaleIds = listOf("natural_minor", "harmonic_minor")
    private val homeScaleIds = listOf("major", "natural_minor", "harmonic_minor")
    private val modeScaleIds = listOf("mixolydian", "dorian")
}
