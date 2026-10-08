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

import com.pandulapeter.campfire.chordpro.model.ChordProSong

/**
 * Converts chords between [ChordNotation.STANDARD], which every file is stored in and the model works in, and the
 * notation a reader prefers. It runs at the two boundaries between the two and nowhere else: on the way to the screen
 * ([toNotation] for a parsed song), and in and out of the editor's field ([convertText]), whose text is shown in the
 * reader's notation and written back in the standard one.
 *
 * German notation writes the note the standard notation calls `B` as `H`, and the note it calls `Bb` as `B`, while the
 * other six letters, the `#` and `b` signs, the quality and the extensions stay as they are, the bass note after `/`
 * rewritten by the same rule as the root. The classical German names spell every accidental out (`Cis`, `Es`, `Ais`),
 * but chord charts in those countries stop at the two letters, and so does this. Latin notation names the seven notes
 * `Do Re Mi Fa Sol La Si` and keeps everything else, and since no Latin name is a standard one, a Latin chord is read
 * as the chord it names wherever it stands, whatever notation the text is said to be in ([read]).
 *
 * The numberings ([ChordNotation.isNumbering]) count the chords from the song's key, which only a parsed song has: a
 * song is shown in them by [toNotation], and a text is never converted into them, nor read as written in them.
 *
 * The lowercase minors of Central European charts (`a` for `Am`, `h` for `Hm`) are a spelling of their own rather than
 * a notation: they are read as the minor chord they stand for in every one, and a conversion keeps them lowercase where
 * the notation has a lowercase spelling, which the Latin one does not.
 */
object ChordProNotation {

    /**
     * Rewrites every chord of a parsed song, which is in [ChordNotation.STANDARD], in [notation]: its key, the chords
     * over its lyrics, the chords of its grids and the rows of chord names above its tabs. Run it last, after any
     * transposition, which works in the standard notation.
     *
     * In a numbering the key, and the key a `{transpose}` names, stay in letters, since a number means nothing without
     * them, and so do the song's definitions, which no page names; a modulation renumbers the stretch it starts from the
     * key it moved to, so that a chorus reads `1 4 5` before it and after it. A song whose key is not a note is left in
     * letters, since the first chord is too weak a guess to give every number on the page its meaning.
     */
    fun toNotation(song: ChordProSong, notation: ChordNotation) = rewriteAt(song, notation)?.let { ChordProChordRewriter.rewriteChords(song, it) } ?: song

    /**
     * Every name [ChordProChords.namesIn] lists for [song], which is in [ChordNotation.STANDARD], mapped to the name
     * [toNotation] shows it under in [notation] where it is first played: in a numbering, a chord played on both sides of
     * a modulation is named by the step it is first played on.
     */
    fun shownNames(song: ChordProSong, notation: ChordNotation): Map<String, String> {
        val rewriteAt = rewriteAt(song, notation)
        val names = LinkedHashMap<String, String>()
        ChordProChords.forEachName(song) { name, offset -> names.getOrPut(name) { rewriteAt?.invoke(offset)?.rename?.invoke(name) ?: name } }
        return names
    }

    /**
     * One chord name in the standard notation as [notation] writes it where it is no step of a key: in a numbering the
     * letters it is written in, which is what a note on its own, or a chord with no song around it, is named by.
     */
    fun shownName(name: String, notation: ChordNotation) = when (notation) {
        ChordNotation.STANDARD, ChordNotation.NASHVILLE, ChordNotation.ROMAN -> name
        ChordNotation.GERMAN -> toGerman(name)
        ChordNotation.LATIN -> toLatin(name)
    }

    /**
     * Rewrites the chords of a raw document written in [from] in [to], leaving every other character where it was.
     * A text read from a file is converted from [ChordNotation.STANDARD], a text somebody typed in their own notation
     * from that one, which is what makes it unambiguous (see [ChordNotation]). Whatever it is converted to, the chords
     * come out with ASCII accidentals, which is how the standard notation is written: so converting a text from the
     * standard notation to itself is what brings a file written before there was one notation, in Latin, or with `♯` and
     * `♭`, into it, and returns any other text unchanged. A numbering is never written into a text: [to] being one is
     * the standard notation, and [from] being one is too.
     *
     * Converting a text out of a notation and back into it returns it, apart from that folding of the accidentals, a
     * German `Bb`, which is read as the B flat its writer meant rather than as a double flat, and a lowercase minor
     * taken through the Latin notation, which has no lowercase spelling to keep it in (`a` comes back as `Am`).
     */
    fun convertText(text: String, from: ChordNotation, to: ChordNotation): String {
        val written = if (from == ChordNotation.GERMAN) null else ChordProParser.parseAsWritten(text)
        val isGerman = written == null || isGermanNotated(written)
        val isWrittenInStandard = to == ChordNotation.STANDARD || to.isNumbering
        // A text typed in the Latin notation is in it throughout, comments and definitions included, so only a text read
        // from a file may be returned as it is: there a Latin word in a comment does not make the file Latin.
        if (from != ChordNotation.LATIN && !isGerman && isWrittenInStandard && text.none { it == SHARP_SIGN || it == FLAT_SIGN } && written?.let(::hasLatinName) == false) return text
        val fromWritten = { name: String -> withAsciiAccidentals(if (isGerman) fromGerman(name) else name) }
        val renameWritten = if (to == ChordNotation.LATIN) {
            { name: String -> toLatin(fromWritten(ChordProChordNames.lowercaseMinorExpanded(name) ?: name)) }
        } else {
            ChordProChordRewriter.keepingLowercaseMinors { name -> shownName(fromWritten(name), to) }
        }
        val rename = { name: String -> ChordProChordNames.latinExpanded(name)?.let { shownName(withAsciiAccidentals(it), to) } ?: renameWritten(name) }
        return ChordProChordRewriter.rewriteChordNamesInText(
            text = text,
            rewriteTab = { lines -> ChordProTabTransposer.rewriteChordNames(lines, rename) },
            rename = rename,
            rewriteDefinition = { rawLine, selector -> ChordProDefinitions.rewrittenLine(rawLine, selector, rename) },
        )
    }

    /**
     * The standard chord [name] stands for as a file or a field writes it: a Latin name as the chord it names, a
     * lowercase minor spelled out, a German one read as German where [isGerman], and the accidentals in ASCII. The
     * expansion comes before the German reading, so that a German `b` (B flat minor) becomes `Bm` and then `Bbm`; a
     * Latin name is never German or a lowercase minor. Anything that is no chord is returned with its accidentals
     * folded and nothing else changed.
     */
    internal fun read(name: String, isGerman: Boolean): String {
        ChordProChordNames.latinExpanded(name)?.let { return withAsciiAccidentals(it) }
        val expanded = ChordProChordNames.lowercaseMinorExpanded(name) ?: name
        return withAsciiAccidentals(if (isGerman) fromGerman(expanded) else expanded)
    }

    /**
     * Rewrites one chord name, its bass note included, in German notation. A word that is not a chord — `N.C.`, or a
     * `[Bridge]` somebody wrote without the `*` that makes it an annotation — is returned as it was, which is what keeps
     * this from turning prose starting with a `B` into prose starting with an `H`.
     */
    internal fun toGerman(name: String): String {
        if (!ChordProChordNames.isChordName(name)) return name
        return ChordProChordNames.rewriteNotes(name, ::noteToGerman)
    }

    /** The same in Latin notation: `Am` is `Lam`, `Bb7` is `Sib7`, `D/f#` is `Re/fa#`. */
    internal fun toLatin(name: String): String {
        if (!ChordProChordNames.isChordName(name)) return name
        return ChordProChordNames.rewriteNotes(name) { note -> latinNames[note.firstOrNull()]?.let { it + note.substring(1) } ?: note }
    }

    /** Whether [name] is a real chord that uses German notation's `H`, at its root or bass, a lowercase `h` minor or bass note included. */
    internal fun isGermanName(name: String) = (ChordProChordNames.lowercaseMinorExpanded(name) ?: name).let { chord ->
        ChordProChordNames.isChordName(chord) && ChordProChordNames.notes(chord).any { it.uppercase().startsWith(GERMAN_B_NATURAL) }
    }

    /** Whether a file's song was written in German notation, which it says by using an `H` chord anywhere. */
    internal fun isGermanNotated(song: ChordProSong) = ChordProChordRewriter.writtenChordNames(song).any(::isGermanName)

    /** Reads a German-notated chord into the notation the rest of the app works in. */
    internal fun fromGerman(name: String): String {
        if (!ChordProChordNames.isChordName(name)) return name
        return ChordProChordNames.rewriteNotes(name, ::noteFromGerman)
    }

    /** The same for a whole song as its file spells it. */
    internal fun fromGerman(song: ChordProSong) = ChordProChordRewriter.rewriteChords(
        song = song,
        rewriteTabLines = { lines -> ChordProTabTransposer.rewriteChordNames(lines, ::fromGerman) },
        rename = ::fromGerman,
    )

    /** [song] with its lowercase minor chords spelled out, and nothing else changed. */
    internal fun withLowercaseMinorsExpanded(song: ChordProSong) = ChordProChordRewriter.rewriteChords(
        song = song,
        rewriteTabLines = { lines -> lines },
        rename = { name -> ChordProChordNames.lowercaseMinorExpanded(name) ?: name },
    )

    /** A chord name with musical accidental signs folded to the ASCII spelling the app writes. */
    internal fun withAsciiAccidentals(name: String) = name.replace(SHARP_SIGN, '#').replace(FLAT_SIGN, 'b')

    /** [song], its file written in [notation], in the app's own: English note names and ASCII accidentals. */
    internal fun normalized(song: ChordProSong, notation: ChordNotation): ChordProSong {
        val names = ChordProChordRewriter.writtenChordNames(song).toList()
        val german = notation == ChordNotation.GERMAN || names.any(::isGermanName)
        val hasLowercaseMinors = names.any { ChordProChordNames.lowercaseMinorExpanded(it) != null }
        if (!german && !hasLowercaseMinors && !hasLatinName(song) && names.none { SHARP_SIGN in it || FLAT_SIGN in it }) return song
        val rename = { name: String -> read(name, german) }
        return ChordProChordRewriter.rewriteChords(song) {
            ChordProChordRewriter.ChordRewrite(
                rewriteTabLines = { lines -> ChordProTabTransposer.rewriteChordNames(lines, rename) },
                rename = rename,
                rewriteDefinition = { ChordProDefinitions.renamedFromNotation(it, rename) },
            )
        }
    }

    /**
     * Whether [song], as its file spells it, names a chord in Latin, its key and its definitions included. Like the German
     * `H`, a Latin name in a comment or a label only counts where another one does: they do not vote, see
     * [ChordProChordRewriter.rewriteChords]. A definition's name is no prose, so it counts on its own.
     */
    private fun hasLatinName(song: ChordProSong) = ChordProChordRewriter.writtenChordNames(song).any { ChordProChordNames.latinExpanded(it) != null } ||
        song.metadata.definitions.any { ChordProChordNames.latinExpanded(it.name) != null } ||
        song.metadata.key?.let { key -> ChordProChordRewriter.renameKey(key) { ChordProChordNames.latinExpanded(it) ?: it } != key } == true

    /**
     * The rewrite of each stretch of [song] in [notation], by the offset a `{transpose}` moved it by, or null where the
     * song stays as it is: in the standard notation, and in a numbering for a song whose key is not a note.
     */
    private fun rewriteAt(song: ChordProSong, notation: ChordNotation): ((Int) -> ChordProChordRewriter.ChordRewrite)? {
        if (notation.isNumbering) {
            val tonic = song.metadata.key?.let(ChordProNashville::tonicOf) ?: return null
            val rewrites = mutableMapOf<Int, ChordProChordRewriter.ChordRewrite>()
            return { offset -> rewrites.getOrPut(offset) { numbering(tonic + offset, isRoman = notation == ChordNotation.ROMAN) } }
        }
        if (notation == ChordNotation.STANDARD) return null
        val rename = { name: String -> shownName(name, notation) }
        val rewrite = ChordProChordRewriter.ChordRewrite(rewriteTabLines = { lines -> ChordProTabTransposer.rewriteChordNames(lines, rename) }, rename = rename)
        return { rewrite }
    }

    private fun numbering(tonic: Int, isRoman: Boolean): ChordProChordRewriter.ChordRewrite {
        val rename = { name: String -> ChordProNashville.number(name, tonic, isRoman) }
        return ChordProChordRewriter.ChordRewrite(
            rewriteTabLines = { lines -> ChordProTabTransposer.rewriteChordNames(lines, rename) },
            rename = rename,
            renameInKey = { it },
            rewriteDefinition = { it },
        )
    }

    private fun noteToGerman(note: String): String {
        // `H` is already the German name of the note, and `Hb` an unusual but unambiguous way of writing the flat one.
        val letter = note.firstOrNull()
        if (letter != 'B' && letter != 'H') return note
        val accidental = note.getOrNull(1)
        return if (accidental == 'b' || accidental == '♭') {
            "B" + note.substring(2) // Bb -> B: the flat is in the letter now, so its sign goes away with it.
        } else {
            "H" + note.substring(1) // B -> H, and B# -> H#, which the classical German names would spell His.
        }
    }

    private fun noteFromGerman(note: String) = when (note.firstOrNull()) {
        'H' -> "B" + note.substring(1)
        'B' -> if (note.getOrNull(1) in accidentalSigns) note else "Bb" + note.substring(1)
        else -> note
    }

    private const val GERMAN_B_NATURAL = "H"
    private const val SHARP_SIGN = '♯'
    private const val FLAT_SIGN = '♭'
    private val accidentalSigns = setOf('#', 'b', '♯', '♭')
    private val latinNames = mapOf('C' to "Do", 'D' to "Re", 'E' to "Mi", 'F' to "Fa", 'G' to "Sol", 'A' to "La", 'B' to "Si")
}
