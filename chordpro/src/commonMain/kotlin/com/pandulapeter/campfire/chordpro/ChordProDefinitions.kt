/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

import com.pandulapeter.campfire.chordpro.model.ChordDefinition
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordVoicing

/**
 * A song's own chord shapes: ChordPro's `{define}` and `{chord}` directives.
 *
 * `{define: G base-fret 1 frets 3 2 0 0 0 3 fingers 2 1 0 0 0 3}` is a fretted shape, its frets counted from the base
 * fret (1 is the base fret itself, 0 an open string, `x`, `X`, `N` and `-1` a muted one), and
 * `{define: G keys 0 4 7}` a keyboard one, its keys counted in semitones from the chord's root. The instrument is the
 * directive's selector where it names one (`{define-ukulele: …}`, `-guitar`, `-keyboard`, `-piano`), and otherwise the
 * one with as many strings as the shape has frets.
 */
object ChordProDefinitions {

    /**
     * The line that defines [voicing] as the shape of [name], the way [read] reads it back:
     * `{define: G base-fret 1 frets 3 2 0 0 0 3 fingers 2 1 0 0 0 3}`, `{define: G keys 0 4 7}`. It carries no
     * selector, since the number of frets already says which instrument a shape is for.
     */
    fun line(name: String, voicing: ChordVoicing) = "{$DEFINE: $name ${shapeOf(name, voicing)}}"

    /** The part of a definition after the chord's name, as [line] writes it. */
    internal fun shapeOf(name: String, voicing: ChordVoicing) = when (voicing) {
        is ChordVoicing.Fretted -> {
            val baseFret = ChordVoicings.baseFret(voicing.frets)
            buildString {
                append("$BASE_FRET $baseFret $FRETS ")
                append(voicing.frets.joinToString(" ") { fret -> if (fret == null) "x" else if (fret == 0) "0" else (fret - baseFret + 1).toString() })
                voicing.fingers?.let { fingers -> append(" $FINGERS ").append(fingers.joinToString(" ")) }
            }
        }
        is ChordVoicing.Keys -> {
            val root = ChordProChords.parse(name)?.root ?: 0
            val keys = (listOfNotNull(voicing.bass) + voicing.notes).map { it - root }
            val octaves = keys.minOrNull()?.takeIf { it < 0 }?.let { (-it + 11) / 12 } ?: 0
            "$KEYS " + keys.joinToString(" ") { (it + octaves * 12).toString() }
        }
    }

    /**
     * The selector of a `{define}` or `{chord}` directive named [name]: empty for one without a selector, the selector
     * for `define-ukulele` and the like, and null for a directive that is no definition at all.
     */
    internal fun selectorOf(name: String): String? {
        val base = name.substringBefore('-')
        if (base !in DEFINITION_DIRECTIVES) return null
        return if ('-' in name) name.substringAfter('-') else ""
    }

    /** The shape a definition with this [value] and [selector] gives its chord, or null where it gives none Campfire draws. */
    internal fun definitionOf(value: String, selector: String): ChordDefinition? =
        (read(value, selector.takeIf { it.isNotEmpty() }) as? Reading.Shape)?.let { ChordDefinition(it.name, it.instrument, it.voicing) }

    /**
     * [definition] moved by [semitones], renamed by [rename] the way the song's chords are, so that it is spelled as
     * they are.
     *
     * A fretted shape moves along the neck by as many frets, every string that sounds with it, the open ones too,
     * which is what a barre or a capo does to a shape. One with an open string never goes down: down by any amount it
     * moves up by the rest of the octave instead, which is the same chord, the way a tab with an open string is moved.
     * For every other shape up by the transposition and down by the rest of the octave are both looked at: one that
     * would take a fret off the neck is out, and of the rest the one a hand can hold is taken
     * ([ChordVoicings.isHoldable]), the lower one where both can or neither can. So a shape high on the neck comes down
     * rather than running off its end, and the lower of the two never needs more fingers than the higher, which is why
     * there and back lands on the shape it started as.
     *
     * The fingering follows where the move is a barre coming or going: open strings a move stops become the first
     * finger's barre and every other finger moves one on, and strings that come to rest open lose their finger while
     * the others move one back. A shape that would need a fifth finger keeps no fingering, and nor does one whose strings
     * coming to rest open were held by more than one finger. A keyboard's keys move with the root they are counted from.
     * [ChordDefinition.movedBy] adds up, so there and back is zero.
     */
    fun transposed(definition: ChordDefinition, semitones: Int, rename: (String) -> String): ChordDefinition {
        val name = rename(definition.name)
        val shift = semitones.mod(12)
        if (shift == 0) return definition.copy(name = name)
        return when (val voicing = definition.voicing) {
            is ChordVoicing.Keys -> {
                val moved = voicing.notes.map { it + shift }
                val octaves = (listOfNotNull(voicing.bass?.plus(shift)) + moved).min() / 12
                definition.copy(
                    name = name,
                    voicing = ChordVoicing.Keys(moved.map { it - octaves * 12 }, voicing.bass?.let { it + shift - octaves * 12 }),
                )
            }
            is ChordVoicing.Fretted -> {
                val candidates = (if (voicing.frets.any { it == 0 }) listOf(shift) else listOf(shift - 12, shift))
                    .filter { move -> voicing.frets.all { it == null || it + move in 0..MAX_FRET } }
                val move = candidates.firstOrNull { ChordVoicings.isHoldable(voicing.frets.map { fret -> fret?.plus(it) }) }
                    ?: candidates.firstOrNull()
                    ?: return definition.copy(name = name)
                definition.copy(name = name, voicing = voicing.movedBy(move), movedBy = definition.movedBy + move)
            }
        }
    }

    private fun ChordVoicing.Fretted.movedBy(move: Int): ChordVoicing.Fretted {
        val moved = frets.map { it?.plus(move) }
        val fingers = fingers?.let { fingers ->
            val stoppedNow = frets.indices.filter { frets[it] == 0 }
            val openedNow = moved.indices.filter { moved[it] == 0 && frets[it] != 0 }
            val adjusted = when {
                stoppedNow.isNotEmpty() -> fingers.mapIndexed { string, finger -> if (string in stoppedNow) 1 else if (finger > 0) finger + 1 else 0 }
                openedNow.isNotEmpty() -> {
                    val released = openedNow.map { fingers[it] }.filter { it > 0 }.toSet()
                    // Strings held by several fingers coming to rest open is no barre going, and moving the shape back
                    // could not tell which finger held which: the fingering is left out rather than guessed.
                    if (released.size > 1) return ChordVoicing.Fretted(moved)
                    fingers.mapIndexed { string, finger ->
                        when {
                            string in openedNow || finger == 0 -> 0
                            else -> finger - released.count { it < finger }
                        }
                    }
                }
                else -> fingers
            }
            adjusted.takeIf { it.all { finger -> finger in 0..MAX_HAND_FINGER } }
        }
        return ChordVoicing.Fretted(moved, fingers)
    }

    /** What reading the value of a definition came to. */
    internal sealed interface Reading {

        /** A shape for an instrument Campfire draws. */
        data class Shape(val name: String, val instrument: ChordInstrument, val voicing: ChordVoicing) : Reading

        /**
         * Valid ChordPro that declares no shape Campfire could draw: a `copy`, a `display`, a `{chord: Am}` that only
         * names a chord, a shape for a number of strings no instrument here has, a selector naming no instrument.
         */
        data class Other(val name: String?) : Reading

        /** A shape that cannot be read: a fret that is no number, `frets` with nothing after it, fingers that do not match. */
        data object Invalid : Reading
    }

    /**
     * Reads the value of a `{define}` or `{chord}` directive (everything after the colon) whose selector, if it has
     * one, is [selector]. A selector that names an instrument and a shape that is for another one, six frets for the
     * ukulele, is [Reading.Invalid]: the file says what it is for, and that is not what it wrote.
     */
    internal fun read(value: String, selector: String? = null): Reading {
        val words = value.trim().split(' ', '\t').filter { it.isNotEmpty() }
        val name = words.firstOrNull() ?: return Reading.Invalid
        var baseFret = 1
        var frets: List<Int?>? = null
        var fingers: List<Int>? = null
        var keys: List<Int>? = null
        var isOther = false
        var index = 1
        fun arguments(): List<String> {
            val start = index
            while (index < words.size && words[index].lowercase() !in keywords) index++
            return words.subList(start, index)
        }
        while (index < words.size) {
            when (words[index++].lowercase()) {
                BASE_FRET -> baseFret = arguments().singleOrNull()?.toIntOrNull()?.takeIf { it >= 1 } ?: return Reading.Invalid
                FRETS -> frets = arguments().takeIf { it.isNotEmpty() }?.map { word ->
                    if (word in mutedFrets) null else word.toIntOrNull()?.takeIf { it >= 0 } ?: return Reading.Invalid
                } ?: return Reading.Invalid
                FINGERS -> fingers = arguments().takeIf { it.isNotEmpty() }?.map { word ->
                    if (word in unusedFingers) 0 else word.toIntOrNull()?.takeIf { it in 0..MAX_FINGER } ?: return Reading.Invalid
                } ?: return Reading.Invalid
                KEYS -> keys = arguments().takeIf { it.isNotEmpty() }?.map { it.toIntOrNull() ?: return Reading.Invalid } ?: return Reading.Invalid
                else -> {
                    // `copy`, `copyall`, `display`, `format` and whatever a later version of the format adds.
                    arguments()
                    isOther = true
                }
            }
        }
        if (fingers != null && fingers.size != frets?.size) return Reading.Invalid
        if (frets != null && keys != null) return Reading.Invalid
        val selected = selector?.let { selectorInstruments[it.lowercase()] }
        if (selector != null && selected == null || isOther) return Reading.Other(name)
        val voicing = when {
            keys != null -> {
                if (selected != null && selected != ChordInstrument.KEYBOARD) return Reading.Invalid
                val root = ChordProChords.parse(name)?.root ?: 0
                val absolute = keys.map { root + it }
                val octaves = absolute.min().let { lowest -> if (lowest < 0) (-lowest + 11) / 12 else 0 }
                return Reading.Shape(name, ChordInstrument.KEYBOARD, ChordVoicing.Keys(absolute.map { it + octaves * 12 }.distinct().sorted()))
            }
            frets != null -> ChordVoicing.Fretted(
                frets = frets.map { fret -> if (fret == null || fret == 0) fret else fret + baseFret - 1 },
                fingers = fingers,
            )
            else -> return Reading.Other(name)
        }
        val instrument = selected ?: ChordInstrument.entries.firstOrNull { it.isFretted && it.tuning.size == frets.size } ?: return Reading.Other(name)
        if (instrument.tuning.size != frets.size) return Reading.Invalid
        return Reading.Shape(name, instrument, voicing)
    }

    internal const val DEFINE = "define"
    internal val DEFINITION_DIRECTIVES = setOf(DEFINE, "chord")
    private const val BASE_FRET = "base-fret"
    private const val FRETS = "frets"
    private const val FINGERS = "fingers"
    private const val KEYS = "keys"
    private const val MAX_FINGER = 5
    private const val MAX_HAND_FINGER = 4
    private const val MAX_FRET = 24
    private val keywords = setOf(BASE_FRET, FRETS, FINGERS, KEYS, "copy", "copyall", "display", "format", "diagram")
    private val mutedFrets = setOf("x", "X", "N", "-1")
    private val unusedFingers = setOf("-", "x", "X", "N")
    private val selectorInstruments = mapOf(
        "guitar" to ChordInstrument.GUITAR,
        "ukulele" to ChordInstrument.UKULELE,
        "keyboard" to ChordInstrument.KEYBOARD,
        "piano" to ChordInstrument.KEYBOARD,
    )
}
