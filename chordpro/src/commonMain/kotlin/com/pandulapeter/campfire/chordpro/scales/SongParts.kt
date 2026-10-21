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
import com.pandulapeter.campfire.chordpro.model.SectionType
import com.pandulapeter.campfire.chordpro.syntax.ChordProChordRewriter
import com.pandulapeter.campfire.chordpro.syntax.ChordProTokens
import com.pandulapeter.campfire.chordpro.tabs.ChordProTabTransposer

/** A song read into the parts [SongScaleGroups] fits scales to: its chorded sections, their chords, and their keys. */
internal object SongParts {

    /**
     * Every section of [song] that plays a chord, its continuations with it, each with the notes of its chords and the
     * roots it plays, and the key in force at it. A `{transpose}` inside a section cuts it, and the rest is a part of
     * its own under the new key, since the transposer has moved its chords. A chorus recall that plays the chords of the
     * chorus before it joins that chorus's part without counting them again; one whose chords differ, a recall after
     * a `{transpose}`, which the transposer has moved, joins the earlier recall playing the same chords, else is a part
     * of its own, of the chorus's kind, so the modulated last choruses of a song weigh as the chorus does before them.
     */
    fun of(song: ChordProSong): List<SongPart> {
        var key = song.metadata.key?.let(::keyOf)
        val capo = song.metadata.capo
        val parts = mutableListOf<SongPart>()
        var chorus: SongPart? = null
        var isModulated = false
        song.blocks.forEach { block ->
            when (block) {
                is ChordProBlock.Transpose -> {
                    key = block.key?.let(::keyOf) ?: key
                    isModulated = true
                }
                is ChordProBlock.Section -> {
                    val chords = playedIn(listOf(block))
                    val previous = parts.lastOrNull()?.takeIf {
                        block.isContinuation && !isModulated && (it.blocks.last() as? ChordProBlock.Section)?.type == block.type
                    }
                    isModulated = false
                    when {
                        previous != null -> {
                            previous.blocks += block
                            previous.add(chords)
                        }
                        chords.isNotEmpty() -> parts += SongPart(mutableListOf(block), key, capo).apply {
                            add(chords)
                            if (block.type == SectionType.Chorus) chorus = this
                        }
                    }
                }
                is ChordProBlock.ChorusRecall -> {
                    val chords = playedIn(block.blocks.filterIsInstance<ChordProBlock.Section>())
                    val names = chords.map { it.name }
                    val recalled = chorus
                    val sameRecall = parts.firstOrNull { part -> part.blocks.all { it is ChordProBlock.ChorusRecall } && part.names == names }
                    when {
                        recalled != null && names == playedIn(recalled.sections).map { it.name } -> recalled.blocks += block
                        sameRecall != null -> sameRecall.blocks += block
                        chords.isNotEmpty() -> parts += SongPart(mutableListOf(block), key, capo).apply { add(chords) }
                    }
                }
                else -> Unit
            }
        }
        return parts.filter { it.roots.isNotEmpty() }
    }

    /** The chords [sections] play, in order, each read once. */
    fun playedIn(sections: List<ChordProBlock.Section>) = sections.flatMap { it.lines.flatMap { line -> line.chordNames() } }.mapNotNull { name ->
        ChordProChords.parse(name)?.let { chord -> rootOf(name)?.let { PlayedChord(name, it, chord) } }
    }

    /** The chord names one line plays, by what kind of line it is: the walk `ChordProChords.namesIn` takes. */
    private fun ChordProLine.chordNames(): List<String> = when (this) {
        is ChordProLine.Lyrics -> chords.filter { !it.isAnnotation }.map { it.name }
        is ChordProLine.Grid -> tokens.filterIsInstance<GridToken.Chord>().flatMap { ChordProTokens.cellChords(it.name) }
        is ChordProLine.Tab -> ChordProTabTransposer.chordNames(listOf(text))
        ChordProLine.Blank -> emptyList()
    }

    /**
     * The key [text] names, minor where what follows its root starts with an `m` other than `ma`, in either case (`Am`,
     * `A minor`, `A Minor`, `A-moll`), so that `Amaj`, `A major`, `A mayor`, and `A Mixolydian` stay major.
     */
    private fun keyOf(text: String): SongKey? = rootOf(text)?.let { root ->
        val mode = ChordProChordRewriter.keyWordOf(text.trim().drop(root.name.length))
        SongKey(root, isMinor = mode.startsWith('m') && mode.getOrNull(1) != 'a' && !mode.startsWith("mixo"))
    }

    /**
     * The root a chord name or a key starts with, spelled as written: `Eb` for `Ebm7` and for `Eb minor`; a key typed
     * with a sign (`B♭`) is spelled `Bb`, the way the chords are.
     */
    private fun rootOf(name: String): SpelledNote? {
        val trimmed = name.trim()
        val letter = trimmed.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'G' } ?: return null
        val accidental = trimmed.getOrNull(1)?.let { accidentalLetters[it] }
        val spelled = letter + (accidental?.toString() ?: "")
        return ChordProChords.pitchClass(spelled)?.let { SpelledNote(it, spelled) }
    }

    private val accidentalLetters = mapOf('#' to '#', '♯' to '#', 'b' to 'b', '♭' to 'b')
}

/**
 * Sections played together, a section with its continuations, or a chorus recall: each of [blocks] a
 * `ChordProBlock.Section` or a `ChordProBlock.ChorusRecall`, under [key] and the header's [capo] (a `{transpose}`
 * renames the key, never the capo).
 */
internal class SongPart(val blocks: MutableList<ChordProBlock>, val key: SongKey?, val capo: Int?) {
    val names = mutableListOf<String>()
    val chords = mutableListOf<Chord>()
    val roots = mutableListOf<SpelledNote>()

    val sections get() = blocks.filterIsInstance<ChordProBlock.Section>()

    /**
     * What a part is of, so that a second verse finds the first: its first section's type, and its label without a
     * number. A recall part's first section is the chorus's own, so a recall is of its chorus's kind.
     */
    val kind get() = blocks.firstNotNullOf { block ->
        (block as? ChordProBlock.ChorusRecall)?.blocks?.filterIsInstance<ChordProBlock.Section>()?.firstOrNull() ?: block as? ChordProBlock.Section
    }.let { it.type to it.label?.trimEnd { char -> char.isDigit() || char.isWhitespace() }?.lowercase() }

    fun add(played: List<PlayedChord>) = played.forEach { (name, root, chord) ->
        names += name
        roots += root
        // The bass of a slash chord is left out: it is a voicing, and a passing bass note is often outside the scale.
        chords += Chord(root = chord.root, intervals = chord.intervals)
    }
}

/** A chord as the song writes it ([name]), on its spelled [root], read as notes ([chord]). */
internal data class PlayedChord(val name: String, val root: SpelledNote, val chord: Chord)

/** A note as the song spells it ([name]), at [pitchClass], from 0 for C to 11 for B. */
internal class SpelledNote(val pitchClass: Int, val name: String)

/** A key the song names, on [root]. */
internal class SongKey(val root: SpelledNote, val isMinor: Boolean)
