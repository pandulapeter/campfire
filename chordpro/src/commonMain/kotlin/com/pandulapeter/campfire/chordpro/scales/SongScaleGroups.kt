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
 * the first of these that some scale on it fits: the key in force at its first section (the song's `{key}`, or the key
 * the last `{transpose}` before it names), the first chord it plays, the last one, then every other root its chords
 * stand on, the most played first.
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
     * The scales to play over every chord of [parts] ([PlayableScales]), on the first root of theirs any are suggested
     * on, or null where none are. A strict fit asks for a scale of at most seven notes holding them, or a dominant blues:
     * the eight-note scales (bebop, flamenco, the diminished ones) hold a passing tone that lets a verse in major and a
     * chorus in Mixolydian share one, which is no answer to which scale each is in. Either way every scale suggested is
     * offered, the eight-note ones among them.
     */
    private fun fitOf(parts: List<Part>, isStrict: Boolean): Fit? {
        val chords = parts.flatMap { it.chords }
        return rootsOf(parts).firstNotNullOfOrNull { root ->
            PlayableScales.suggestionFor(root.pitchClass, chords)?.takeIf { !isStrict || it.isFit }?.let { Fit(root, it.scales) }
        }
    }

    private fun rootsOf(parts: List<Part>): List<Root> {
        val played = parts.flatMap { it.roots }
        val byCount = played.groupingBy { it.pitchClass }.eachCount()
        return (
            parts.mapNotNull { it.key } +
                listOfNotNull(parts.first().roots.firstOrNull(), parts.last().roots.lastOrNull()) +
                played.sortedByDescending { byCount[it.pitchClass] }
            ).distinctBy { it.pitchClass }
    }

    /**
     * Every section of [song] that plays a chord, its continuations with it, a chorus recall left out since it plays
     * the chorus again, each with the notes of its chords and the roots it plays, and the key in force at it.
     */
    private fun partsOf(song: ChordProSong): List<Part> {
        var key = song.metadata.key?.let(::rootOf)
        val parts = mutableListOf<Part>()
        song.blocks.forEach { block ->
            when (block) {
                is ChordProBlock.Transpose -> key = block.key?.let(::rootOf) ?: key
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

    /** The root a chord name or a key starts with, spelled as written: `Eb` for `Ebm7` and for `Eb minor`. */
    private fun rootOf(name: String): Root? {
        val trimmed = name.trim()
        val letter = trimmed.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'G' } ?: return null
        val accidental = trimmed.getOrNull(1)?.takeIf { it == '#' || it == 'b' }
        val spelled = letter + (accidental?.toString() ?: "")
        return ChordProChords.pitchClass(spelled)?.let { Root(it, spelled) }
    }

    private class Part(val sections: MutableList<ChordProBlock.Section>, val key: Root?) {
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
}
