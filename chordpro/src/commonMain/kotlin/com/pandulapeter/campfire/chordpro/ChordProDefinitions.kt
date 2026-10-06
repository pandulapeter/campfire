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
     * selector, since the number of frets already says which instrument a shape is for. A keyboard's keys are counted
     * from the root of [name] as it is spelled in [notation].
     */
    fun line(name: String, voicing: ChordVoicing, notation: ChordNotation = ChordNotation.STANDARD) = "{$DEFINE: $name ${shapeOf(name, voicing, notation)}}"

    /** The part of a definition after the chord's name, as [line] writes it. */
    internal fun shapeOf(name: String, voicing: ChordVoicing, notation: ChordNotation = ChordNotation.STANDARD) = when (voicing) {
        is ChordVoicing.Fretted -> {
            val baseFret = ChordVoicings.baseFret(voicing.frets)
            buildString {
                append("$BASE_FRET $baseFret $FRETS ")
                append(voicing.frets.joinToString(" ") { fret -> if (fret == null) "x" else if (fret == 0) "0" else (fret - baseFret + 1).toString() })
                voicing.fingers?.let { fingers -> append(" $FINGERS ").append(fingers.joinToString(" ")) }
            }
        }
        is ChordVoicing.Keys -> {
            val root = ChordProChords.parse(name, notation)?.root ?: 0
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

    /**
     * [rawLine], a `{define}` or `{chord}` directive whose selector is [selector], with the chords it names renamed by
     * [rename] and, where it holds a fretted shape it can read, the shape moved by [semitones] the way [transposed]
     * moves it: the name, the base fret, the frets and the fingers each rewritten where it stands and every other
     * character of the line kept, a selector, a `display` and the spacing included. A base fret is written as a diagram
     * would draw the shape, and a line that had none counts its frets from the nut whatever they come to, so that
     * moving it back writes the line it was; a fingering the move leaves out goes with its keyword. A keyboard's keys are counted from the root and stay as
     * they are. A line that cannot be read is left byte for byte, as a tab that fits in no octave is.
     */
    fun rewrittenLine(rawLine: String, selector: String, rename: (String) -> String, semitones: Int = 0): String {
        val trimmed = rawLine.trim()
        val indent = rawLine.length - rawLine.trimStart().length
        val valueStart = ChordProSyntax.directiveValueStart(trimmed)?.let { it + indent } ?: return rawLine
        val valueEnd = indent + trimmed.lastIndexOf('}')
        if (valueEnd <= valueStart) return rawLine
        val value = rawLine.substring(valueStart, valueEnd)
        val reading = read(value, selector.takeIf { it.isNotEmpty() })
        if (reading == Reading.Invalid) return rawLine
        val words = mutableListOf<IntRange>()
        var index = 0
        while (index < value.length) {
            if (value[index].isWhitespace()) {
                index++
                continue
            }
            val start = index
            while (index < value.length && !value[index].isWhitespace()) index++
            words += start until index
        }
        val replacements = mutableMapOf<Int, String>()
        val removals = mutableSetOf<Int>()
        replacements[0] = rename(value.substring(words[0]))
        val keywordIndices = words.indices.filter { value.substring(words[it]).lowercase() in keywords }
        // The other chord a `copy` names is renamed with the one it defines.
        keywordIndices.filter { value.substring(words[it]).lowercase() == COPY }.forEach { keyword ->
            words.getOrNull(keyword + 1)?.let { replacements[keyword + 1] = rename(value.substring(it)) }
        }
        val voicing = (reading as? Reading.Shape)?.voicing as? ChordVoicing.Fretted
        if (voicing != null && semitones.mod(12) != 0) {
            val moved = transposed(ChordDefinition(reading.name, reading.instrument, voicing), semitones) { it }.voicing as ChordVoicing.Fretted
            val baseFretKeyword = keywordIndices.firstOrNull { value.substring(words[it]).lowercase() == BASE_FRET }
            // A line with no base fret counts its frets from the nut whatever they are, so that moving it back writes
            // the line it was.
            val baseFret = if (baseFretKeyword == null) 1 else ChordVoicings.baseFret(moved.frets)
            fun argumentsOf(keyword: String) = keywordIndices.firstOrNull { value.substring(words[it]).lowercase() == keyword }?.let { start ->
                (start + 1 until (keywordIndices.firstOrNull { it > start } ?: words.size)).toList()
            }
            argumentsOf(FRETS)?.forEachIndexed { string, word ->
                val fret = moved.frets[string]
                // A muted string keeps the way the file writes it, `x`, `N` or `-1`.
                if (fret != null) replacements[word] = if (fret == 0) "0" else (fret - baseFret + 1).toString()
            }
            if (baseFretKeyword != null) argumentsOf(BASE_FRET)?.firstOrNull()?.let { replacements[it] = baseFret.toString() }
            val fingersKeyword = keywordIndices.firstOrNull { value.substring(words[it]).lowercase() == FINGERS }
            if (fingersKeyword != null) {
                val arguments = argumentsOf(FINGERS).orEmpty()
                val fingers = moved.fingers
                if (fingers == null) {
                    removals += fingersKeyword
                    removals += arguments
                } else {
                    arguments.forEachIndexed { string, word ->
                        // An unused string keeps the way the file writes it, `0`, `-` or `x`.
                        if (fingers[string] > 0 || value.substring(words[word]) !in unusedFingers) replacements[word] = fingers[string].toString()
                    }
                }
            }
        }
        val rewritten = buildString {
            var consumedUntil = 0
            words.forEachIndexed { word, range ->
                if (word in removals) {
                    // The whitespace in front of a word that goes goes with it.
                    var cut = range.first
                    while (cut > consumedUntil && value[cut - 1].isWhitespace()) cut--
                    append(value, consumedUntil, cut)
                } else {
                    append(value, consumedUntil, range.first)
                    append(replacements[word] ?: value.substring(range))
                }
                consumedUntil = range.last + 1
            }
            append(value, consumedUntil, value.length)
        }
        return rawLine.substring(0, valueStart) + rewritten + rawLine.substring(valueEnd)
    }

    /**
     * The chord of the brackets [offset] is in or touching on its line of [text] — the caret right after `[G]` or right
     * before it included — or null where it is in none, or in one that holds an annotation or nothing.
     */
    fun chordAt(text: String, offset: Int): String? {
        val starts = ChordProSyntax.lineStartOffsets(text)
        val line = starts.indexOfLast { it <= offset }.coerceAtLeast(0)
        val column = offset - starts[line]
        return ChordProSyntax.brackets(ChordProSyntax.splitLines(text)[line])
            .firstOrNull { column >= it.range.first && column <= it.range.last + 1 }
            ?.content?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("*") }
    }

    /**
     * Where the shape of [name] on [instrument] is written in [text]: its frets, or its keys, from the first to the last,
     * on the line that defines it (the last one, which is the one that counts), or null where [text] defines no shape
     * of it there. What an editor sends the caret to, rather than writing the chord a second line.
     */
    fun rangeOf(text: String, name: String, instrument: ChordInstrument): IntRange? {
        val starts = ChordProSyntax.lineStartOffsets(text)
        return ChordProSyntax.splitLines(text).withIndex().reversed().firstNotNullOfOrNull { (index, line) ->
            val trimmed = line.trim()
            val directive = ChordProSyntax.matchDirective(trimmed) ?: return@firstNotNullOfOrNull null
            val selector = selectorOf(directive.name) ?: return@firstNotNullOfOrNull null
            val shape = directive.value?.let { read(it, selector.takeIf { selector -> selector.isNotEmpty() }) } as? Reading.Shape
            if (shape == null || shape.name != name || shape.instrument != instrument) return@firstNotNullOfOrNull null
            val keyword = if (instrument == ChordInstrument.KEYBOARD) KEYS else FRETS
            val lineStart = starts[index] + (line.length - line.trimStart().length)
            val keywordAt = Regex("(?i)(^|\\s)$keyword(\\s)").find(trimmed) ?: return@firstNotNullOfOrNull null
            var start = keywordAt.range.last + 1
            while (start < trimmed.length && trimmed[start].isWhitespace()) start++
            var end = start
            var last = start
            while (end < trimmed.length && trimmed[end] != '}') {
                val wordStart = end
                while (end < trimmed.length && !trimmed[end].isWhitespace() && trimmed[end] != '}') end++
                if (trimmed.substring(wordStart, end).lowercase() in keywords) break
                if (end > wordStart) last = end
                while (end < trimmed.length && trimmed[end].isWhitespace()) end++
            }
            (lineStart + start) until (lineStart + last)
        }
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

        /**
         * A shape that cannot be read: a fret that is no number, `frets` with nothing after it, fingers that do not match, a
         * fret off the neck, a base fret past the last fret, a value given twice.
         */
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
        val given = mutableSetOf<String>()
        fun arguments(): List<String> {
            val start = index
            while (index < words.size && words[index].lowercase() !in keywords) index++
            return words.subList(start, index)
        }
        while (index < words.size) {
            val keyword = words[index++].lowercase()
            // A value said twice is ambiguous to its reader as well, and rewriting one of the two would leave the other
            // describing a shape that is no longer there.
            if (keyword in valueKeywords && !given.add(keyword)) return Reading.Invalid
            when (keyword) {
                BASE_FRET -> baseFret = arguments().singleOrNull()?.toIntOrNull()?.takeIf { it in 1..MAX_FRET } ?: return Reading.Invalid
                FRETS -> frets = arguments().takeIf { it.isNotEmpty() }?.map { word ->
                    if (word in mutedFrets) null else word.toIntOrNull()?.takeIf { it in 0..MAX_FRET } ?: return Reading.Invalid
                } ?: return Reading.Invalid
                FINGERS -> fingers = arguments().takeIf { it.isNotEmpty() }?.map { word ->
                    if (word in unusedFingers) 0 else word.toIntOrNull()?.takeIf { it in 0..MAX_FINGER } ?: return Reading.Invalid
                } ?: return Reading.Invalid
                // Keys past the diagram are wrapped by their note rather than refused, as the specification says, which also keeps
                // what a diagram draws to four octaves however large a number the file holds.
                KEYS -> keys = arguments().takeIf { it.isNotEmpty() }?.map { word ->
                    val key = word.toIntOrNull() ?: return Reading.Invalid
                    when {
                        key > MAX_KEY -> MAX_KEY - 11 + key.mod(12)
                        key < MIN_KEY -> MIN_KEY + key.mod(12)
                        else -> key
                    }
                } ?: return Reading.Invalid
                // A copy declares the shape of another chord, which is that chord's to draw.
                COPY, COPY_ALL -> {
                    arguments()
                    isOther = true
                }
                // `display`, `format` and `diagram` say how a shape is shown rather than what it is.
                else -> arguments()
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
                val notes = absolute.map { it + octaves * 12 }.map { if (it > MAX_KEY) MAX_KEY - 11 + it.mod(12) else it }
                return Reading.Shape(name, ChordInstrument.KEYBOARD, ChordVoicing.Keys(notes.distinct().sorted()))
            }
            // Both operands are already on the neck, so the sum cannot overflow; past the last fret is off it.
            frets != null -> ChordVoicing.Fretted(
                frets = frets.map { fret -> if (fret == null || fret == 0) fret else (fret + baseFret - 1).takeIf { it <= MAX_FRET } ?: return Reading.Invalid },
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
    private const val MIN_KEY = -24
    private const val MAX_KEY = 47
    private const val COPY = "copy"
    private const val COPY_ALL = "copyall"
    private val valueKeywords = setOf(BASE_FRET, FRETS, FINGERS, KEYS)
    private val keywords = setOf(BASE_FRET, FRETS, FINGERS, KEYS, COPY, COPY_ALL, "display", "format", "diagram")
    private val mutedFrets = setOf("x", "X", "N", "-1")
    private val unusedFingers = setOf("-", "x", "X", "N")
    private val selectorInstruments = mapOf(
        "guitar" to ChordInstrument.GUITAR,
        "ukulele" to ChordInstrument.UKULELE,
        "keyboard" to ChordInstrument.KEYBOARD,
        "piano" to ChordInstrument.KEYBOARD,
    )
}
