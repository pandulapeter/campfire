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
 * fret (1 is the base fret itself, 0 an open string, `x`, `X`, `N` and `-1` a muted one; `base_fret` is read too), its
 * fingers numbered 1 to 5 and every other finger value, a thumb's `T` included, shown as none, as the specification
 * has it ignored, and `{define: G keys 0 4 7}` a keyboard one, its keys counted in semitones from the chord's root. The instrument is the
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
     * would take a fret off the neck is out (and a shape no move keeps on the neck stays as it is, named as it was), and
     * of the rest the one a hand can hold is taken ([ChordVoicings.isHoldable]), the lower one where both can or neither
     * can. So a shape high on the neck comes down
     * rather than running off its end, and the lower of the two never needs more fingers than the higher, which is why
     * there and back lands on the shape it started as.
     *
     * The fingering follows where the move is a barre coming or going: open strings a move stops become the first
     * finger's barre and every other finger moves one on, and strings that come to rest open lose their finger while
     * the others move one back. A shape that would need a fifth finger keeps no fingering here, on the page (the text keeps
     * it, see [rewrittenLine]), and nor does one whose strings coming to rest open were held by more than one finger. A keyboard's keys move with the root they are counted from.
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
                // A shape no move keeps on the neck is not the new chord's, so it is left as the old chord's.
                val (move, moved) = voicing.moved(semitones, ChordVoicings.MAX_FINGERS) ?: return definition
                definition.copy(name = name, voicing = moved, movedBy = definition.movedBy + move)
            }
        }
    }

    /**
     * The move along the neck [transposed] takes this shape by for [semitones], and the shape it lands on with its
     * fingering kept up to [maxFinger] fingers, or null where no move keeps every fret on the neck.
     */
    private fun ChordVoicing.Fretted.moved(semitones: Int, maxFinger: Int): Pair<Int, ChordVoicing.Fretted>? {
        val shift = semitones.mod(12)
        val candidates = (if (frets.any { it == 0 }) listOf(shift) else listOf(shift - 12, shift))
            .filter { move -> frets.all { it == null || it + move in 0..ChordVoicings.MAX_FRET } }
        val move = candidates.firstOrNull { ChordVoicings.isHoldable(frets.map { fret -> fret?.plus(it) }) }
            ?: candidates.firstOrNull()
            ?: return null
        return move to movedBy(move, maxFinger)
    }

    /**
     * [definition], read from a text in another notation, renamed by [rename] into the standard one: a keyboard's keys,
     * which [read] counted from the root the name has in the standard notation, counted from the root of its new name.
     */
    internal fun renamedFromNotation(definition: ChordDefinition, rename: (String) -> String): ChordDefinition {
        val name = rename(definition.name)
        val voicing = definition.voicing as? ChordVoicing.Keys ?: return definition.copy(name = name)
        // The same fallback [read] took, so that this undoes exactly what it assumed.
        val root = ChordProChords.parse(definition.name)?.root ?: 0
        return definition.copy(name = name, voicing = keysShape(name, (listOfNotNull(voicing.bass) + voicing.notes).map { it - root }))
    }

    private fun ChordVoicing.Fretted.movedBy(move: Int, maxFinger: Int): ChordVoicing.Fretted {
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
            adjusted.takeIf { it.all { finger -> finger in 0..maxFinger } }
        }
        return ChordVoicing.Fretted(moved, fingers)
    }

    /**
     * [rawLine], a `{define}` or `{chord}` directive whose selector is [selector], with the chords it names renamed by
     * [rename] and, where it holds a fretted shape it can read, the shape moved by [semitones] the way [transposed]
     * moves it: the name, the base fret, the frets and the fingers each rewritten where it stands and every other
     * character of the line kept, a selector, a `display` and the spacing included. A base fret is written as a diagram
     * would draw the shape, and a line that had none counts its frets from the nut whatever they come to, so that
     * moving it back writes the line it was; a fingering is kept up to five fingers, and one the move cannot carry back
     * (strings held by several fingers coming to rest open) goes with its keyword. A keyboard's keys are counted from the
     * root and stay as they are. A line that cannot be read is left byte for byte, as a tab that fits in no octave is, and
     * so is one whose shape no move keeps on the neck.
     */
    internal fun rewrittenLine(rawLine: String, selector: String, rename: (String) -> String, semitones: Int = 0): String {
        val trimmed = rawLine.trim()
        val indent = rawLine.length - rawLine.trimStart().length
        val valueStart = ChordProDirectives.directiveValueStart(trimmed)?.let { it + indent } ?: return rawLine
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
            // The text keeps a fingering up to the five fingers the specification allows, where the page draws none past
            // the hand's four: dropping it from the file would lose it for good, since moving back could not bring it back.
            val moved = voicing.moved(semitones, MAX_FINGER)?.second ?: return rawLine
            val baseFretKeyword = keywordIndices.firstOrNull { keywordOf(value.substring(words[it])) == BASE_FRET }
            // A line with no base fret counts its frets from the nut whatever they are, so that moving it back writes
            // the line it was.
            val baseFret = if (baseFretKeyword == null) 1 else ChordVoicings.baseFret(moved.frets)
            fun argumentsOf(keyword: String) = keywordIndices.firstOrNull { keywordOf(value.substring(words[it])) == keyword }?.let { start ->
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
                        // A finger the move leaves as it was keeps the way the file writes it, `0`, `-`, `x` or a thumb's `T`.
                        if (fingers[string] != voicing.fingers?.get(string)) replacements[word] = fingers[string].toString()
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
        val starts = ChordProLines.lineStartOffsets(text)
        val line = starts.indexOfLast { it <= offset }.coerceAtLeast(0)
        val column = offset - starts[line]
        return ChordProDirectives.brackets(ChordProLines.splitLines(text)[line])
            .firstOrNull { column >= it.range.first && column <= it.range.last + 1 }
            ?.content?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("*") }
    }

    /**
     * Where the shape of [name] on [instrument] is written in [text]: its frets, or its keys, from the first to the last,
     * on the line that defines it (the one that counts: the last `{define}`, or the last `{chord}` where there is none),
     * or null where [text] defines no shape of it there. What an editor sends the caret to, rather than writing the chord
     * a second line.
     */
    fun rangeOf(text: String, name: String, instrument: ChordInstrument): IntRange? {
        val starts = ChordProLines.lineStartOffsets(text)
        val lines = ChordProLines.splitLines(text).withIndex().reversed()
        fun rangeOn(isDefine: Boolean) = lines.firstNotNullOfOrNull { (index, line) ->
            val trimmed = line.trim()
            val directive = ChordProDirectives.matchDirective(trimmed) ?: return@firstNotNullOfOrNull null
            if ((directive.name.substringBefore('-') == DEFINE) != isDefine) return@firstNotNullOfOrNull null
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
        return rangeOn(isDefine = true) ?: rangeOn(isDefine = false)
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
            val keyword = keywordOf(words[index++])
            // A value said twice is ambiguous to its reader as well, and rewriting one of the two would leave the other
            // describing a shape that is no longer there.
            if (keyword in valueKeywords && !given.add(keyword)) return Reading.Invalid
            when (keyword) {
                BASE_FRET -> baseFret = arguments().singleOrNull()?.toIntOrNull()?.takeIf { it in 1..ChordVoicings.MAX_FRET } ?: return Reading.Invalid
                FRETS -> frets = arguments().takeIf { it.isNotEmpty() }?.map { word ->
                    if (word in mutedFrets) null else word.toIntOrNull()?.takeIf { it in 0..ChordVoicings.MAX_FRET } ?: return Reading.Invalid
                } ?: return Reading.Invalid
                // The specification has every value but a finger's number ignored: a thumb's `T`, a letter, a `-`.
                FINGERS -> fingers = arguments().takeIf { it.isNotEmpty() }?.map { word ->
                    word.toIntOrNull()?.takeIf { it in 1..MAX_FINGER } ?: 0
                } ?: return Reading.Invalid
                KEYS -> keys = arguments().takeIf { it.isNotEmpty() }?.map { it.toIntOrNull() ?: return Reading.Invalid } ?: return Reading.Invalid
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
                return Reading.Shape(name, ChordInstrument.KEYBOARD, keysShape(name, keys))
            }
            // Both operands are already on the neck, so the sum cannot overflow; past the last fret is off it.
            frets != null -> ChordVoicing.Fretted(
                frets = frets.map { fret -> if (fret == null || fret == 0) fret else (fret + baseFret - 1).takeIf { it <= ChordVoicings.MAX_FRET } ?: return Reading.Invalid },
                fingers = fingers,
            )
            else -> return Reading.Other(name)
        }
        val instrument = selected ?: ChordInstrument.entries.firstOrNull { it.isFretted && it.tuning.size == frets.size } ?: return Reading.Other(name)
        if (instrument.tuning.size != frets.size) return Reading.Invalid
        return Reading.Shape(name, instrument, voicing)
    }

    /**
     * The keyboard shape of [name] whose [keys] are counted from its root. Keys past the diagram are wrapped by their
     * note rather than refused, as the specification says, which also keeps what a diagram draws to four octaves however
     * large a number the file holds. A slash chord's lowest key is its bass, drawn apart from the chord, where it is
     * written the way [line] writes one: its note sounding again above it, or no note of the chord without it. A
     * one-hand inversion written by hand (`{define: C/E keys 4 7 12}`) stays three keys played together.
     */
    private fun keysShape(name: String, keys: List<Int>): ChordVoicing.Keys {
        val chord = ChordProChords.parse(name)
        val root = chord?.root ?: 0
        val absolute = keys.map { key ->
            root + when {
                key > ChordVoicings.MAX_KEY -> ChordVoicings.MAX_KEY - 11 + key.mod(12)
                key < MIN_KEY -> MIN_KEY + key.mod(12)
                else -> key
            }
        }
        val octaves = absolute.min().let { lowest -> if (lowest < 0) (-lowest + 11) / 12 else 0 }
        val notes = absolute.map { it + octaves * 12 }.map { if (it > ChordVoicings.MAX_KEY) ChordVoicings.MAX_KEY - 11 + it.mod(12) else it }.distinct().sorted()
        val bass = chord?.bass
        val lowest = notes.first()
        if (bass != null && notes.size > 1 && lowest.mod(12) == bass) {
            val above = notes.drop(1)
            val chordNotes = chord.intervals.map { (chord.root + it) % 12 }
            if (above.any { it.mod(12) == bass } || bass !in chordNotes) return ChordVoicing.Keys(above, bass = lowest)
        }
        return ChordVoicing.Keys(notes)
    }

    /** The keyword [word] is, with the specification's `base_fret` read as the `base-fret` it spells too. */
    private fun keywordOf(word: String) = word.lowercase().let { if (it in baseFretKeywords) BASE_FRET else it }

    internal const val DEFINE = "define"
    internal val DEFINITION_DIRECTIVES = setOf(DEFINE, "chord")
    private const val BASE_FRET = "base-fret"
    private val baseFretKeywords = setOf(BASE_FRET, "base_fret")
    private const val FRETS = "frets"
    private const val FINGERS = "fingers"
    private const val KEYS = "keys"
    private const val MAX_FINGER = 5
    private const val MIN_KEY = -24
    private const val COPY = "copy"
    private const val COPY_ALL = "copyall"
    private val valueKeywords = setOf(BASE_FRET, FRETS, FINGERS, KEYS)
    private val keywords = baseFretKeywords + setOf(FRETS, FINGERS, KEYS, COPY, COPY_ALL, "display", "format", "diagram")
    private val mutedFrets = setOf("x", "X", "N", "-1")
    private val selectorInstruments = mapOf(
        "guitar" to ChordInstrument.GUITAR,
        "ukulele" to ChordInstrument.UKULELE,
        "keyboard" to ChordInstrument.KEYBOARD,
        "piano" to ChordInstrument.KEYBOARD,
    )
}
