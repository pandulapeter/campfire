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

import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.ascii
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.isChordOrLatinName
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.parentheses

/** The labelled values a sheet names its key, capo, tempo, time, and credits with, read from a line of it. */
internal object ChordSheetMetadata {
    /**
     * A label with a colon (or the semicolon some sites print instead) is metadata whatever follows it, except a label of
     * another language that is an ordinary word too ("Tom", "Metro", "Mesure"), which takes a value of its field's shape
     * only. Without a colon only a value of the field's own shape is
     * (`Capo 2`, `Key G`, `Time 4/4`), since a lyric may well start with one of these words: "By the rivers of
     * Babylon" and "Time after time" are sung, not credited. A value that cannot be read comes back blank, and its line
     * then stays a line of text rather than vanishing ([LineKinds]). A line holding several details one after another
     * with no gap between them (`Tuning: E A D G B EKey: G`) is a row ([labelledRow]), not one value, and a capo of none
     * (`Capo: No capo`, `Capo: 0`) is [NO_CAPO]. The details a site prints besides these are [ChordSheetSiteDetails]'.
     */
    internal fun metadata(text: String): Pair<String, String>? {
        if (noCapo.matches(text.trim())) return NO_CAPO to NO_CAPO
        if (!labelGap.containsMatchIn(text.trim()) && ChordSheetSiteDetails.parts(text).size > 1) return null
        val match = metadata.matchEntire(text.trim()) ?: unlabelledMetadata.matchEntire(text.trim())?.takeIf {
            val value = it.groupValues[2].trim()
            when (it.groupValues[1].lowercase()) {
                "tempo" -> value.first().isDigit()
                "time" -> timeSignature.matches(value)
                in keyLabels -> isChordOrLatinName(ascii(value))
                in capoLabels -> value.replace(capoPreposition, "").let { it.firstOrNull()?.isDigit() == true || romanCapo.matches(it.uppercase()) }
                else -> true
            }
        }
        if (match != null) {
            val label = match.groupValues[1].lowercase().replace(" & ", " and ")
            val name = when (label) {
                in keyLabels -> "key"
                in capoLabels -> "capo"
                "temp\u00f3", "bpm", "\u05d8\u05de\u05e4\u05d5" -> "tempo"
                "\u00fctemmutat\u00f3", in foreignTimeLabels -> "time"
                "el\u0151ad\u00f3" -> "artist"
                "by", "words and music by", "words and music", in foreignCreditLabels -> CREDIT
                in composerLabels -> "composer"
                in lyricistLabels -> "lyricist"
                "time signature" -> "time"
                "song" -> "title"
                else -> label
            }
            val value = match.groupValues[2].trim()
            // A tempo is a number of beats, which Campfire's PDF prints with its unit: "96 BPM".
            // A capo marked with its shapes' key is read so only where its fret reads too: `Capo: II (G shapes)` stays
            // the line of text an unreadable capo is.
            val capoValue = value.replace(capoPreposition, "")
            if (name == "capo" && (noCapoValue.matches(value) || capo(capoValue) == "0")) return NO_CAPO to NO_CAPO
            if (name == "capo" && shapesKeyOf(capoValue) != null && capo(capoValue).isNotBlank()) return CAPO_SHAPES to capoValue
            return (if (name == "capo" && isSoundingCapo(value)) CAPO_SOUNDING else name) to when (name) {
                "capo" -> capo(capoValue)
                "tempo" -> Regex("^[0-9]+").find(value)?.value ?: value.takeIf { label in setOf("tempo", "temp\u00f3", "bpm") }.orEmpty()
                // A time signature label of another language is as ordinary a word ("Metro", "Mesure"), so it is read
                // with a value of a time signature only, as the foreign key labels are.
                "time" -> value.takeIf { label !in foreignTimeLabels || timeSignature.matches(value) }.orEmpty()
                // The labels the sites outside the English-speaking world write are read with a value of a key only,
                // since a line such as "Tom: here we go" is as likely a cue in the lyrics.
                "key" -> value.takeIf { label in setOf("key", "hangnem") || isChordOrLatinName(ascii(value)) }.orEmpty()
                else -> value
            }
        }
        ChordSheetSiteDetails.detail(text)?.let { return it }
        bpm.matchEntire(text.trim())?.let { return "tempo" to it.groupValues[1] }
        if (text.trim().startsWith('\u00a9')) return "copyright" to text.trim().removePrefix("\u00a9").trim()
        return null
    }

    /**
     * The values a line names, where it names nothing else, each set apart from the next by a gap: the row
     * `Key: A   Transposition: +2   Capo: 2   Tempo: 96 BPM   Time: 3/4` Campfire's PDF heads a song with ([isHeader]),
     * or `Tempo: 90   Time: 3/4`, which anywhere in a song is the change the PDF prints one as. Labelled with a colon
     * and with a value of the field's shape, since anywhere in a song a line starting with "Time" is far more likely sung
     * than credited. A transposition is among them for the caller to leave out rather than write: the chords it is
     * printed over are already in the key it took them to. A chord site's details block, read with its elements'
     * boundaries lost, is such a row with its labels glued onto the values before them ([ChordSheetSiteDetails.parts]);
     * its tuning, the capo it says there is none of, and the values that are the site's (an author's credit beside them
     * among them, [ChordSheetSiteDetails.transcriber]) are read in the song's header.
     */
    internal fun labelledRow(text: String, isHeader: Boolean): List<Pair<String, String>>? {
        val parts = text.trim().split(labelGap).flatMap(ChordSheetSiteDetails::parts)
        val hasSiteValue = parts.any { ChordSheetSiteDetails.detail(it)?.first == ChordSheetSiteDetails.DETAIL }
        return parts.mapNotNull { part ->
            transposition.matchEntire(part)?.let { TRANSPOSITION to it.groupValues[2] }
                ?: metadata(part)?.takeIf { metadata.matchEntire(part) != null || it.first in siteNames }
                ?: ChordSheetSiteDetails.transcriber(part)?.takeIf { hasSiteValue }
        }.takeIf { entries ->
            entries.size == parts.size && entries.all { (name, value) ->
                when (name) {
                    "tempo" -> value.firstOrNull()?.isDigit() == true
                    "time" -> timeSignature.matches(value)
                    "key" -> isHeader && isChordOrLatinName(ascii(value))
                    "capo", CAPO_SOUNDING, CAPO_SHAPES -> isHeader && value.isNotEmpty()
                    TRANSPOSITION, NO_CAPO, ChordSheetSiteDetails.DETAIL -> isHeader
                    ChordSheetSiteDetails.TUNING -> isHeader && value.isNotEmpty()
                    else -> false
                }
            }
        }
    }

    /** Whether a capo [value] is Campfire's PDF saying the chords under it are named as they sound (`2 (chords as they sound)`). */
    private fun isSoundingCapo(value: String) = parentheses.find(value)?.groupValues?.get(1)?.trim()?.lowercase() in soundingCapoMarkers

    /**
     * The key a capo [value] names as its shapes' in Campfire's PDF (`2 (Em shapes)`, `2 (Em-Griffe)`), cut from the
     * original text so its case survives (`em` is no chord); null where the parentheses hold no such marker.
     */
    internal fun shapesKeyOf(value: String): String? {
        val marked = parentheses.find(value)?.groupValues?.get(1)?.trim() ?: return null
        val lowercase = marked.lowercase()
        return shapesCapoMarkers.firstNotNullOfOrNull { (prefix, suffix) ->
            if (lowercase.length > prefix.length + suffix.length && lowercase.startsWith(prefix) && lowercase.endsWith(suffix)) {
                marked.substring(prefix.length, marked.length - suffix.length).trim().takeIf { isChordOrLatinName(ascii(it)) }
            } else {
                null
            }
        }
    }

    /** The fret a capo value names, in digits or in the Roman numerals many sites write it in; blank where it names none. */
    internal fun capo(value: String): String = Regex("[0-9]+").find(value)?.value
        ?: romanCapo.matchEntire(value.uppercase())?.groupValues?.get(1)?.let { roman -> (romanNumerals.indexOf(roman) + 1).toString() }
        ?: ""

    private val keyLabels = setOf(
        "key", "key of", "hangnem", "t\u00f3nina", "tom", "tono", "tonalidad", "tonart", "tonalit\u00e9", "tonalit\u00e0", "tonacja", "toonsoort",
        "tonalitate", "\u05d8\u05d5\u05e0\u05dc\u05d9\u05d5\u05ea",
    )
    private val capoLabels = setOf(
        "capo", "capotraste", "cejilla", "kapodaster", "kapodastr", "capodastre", "kapo", "capodastru", "capotasto", "\u05e7\u05d0\u05e4\u05d5",
    )

    /**
     * The words Campfire's PDF puts in parentheses after a capo whose chords are named as they sound, in every language
     * the app prints them in (`print_capo_sounding`), lowercase.
     */
    private val soundingCapoMarkers = setOf(
        "chords as they sound",
        "hangz\u00f3 akkordok",
        "acorduri cum sun\u0103",
        "klingende akkorde",
        "acordes como suenan",
        "accords en son r\u00e9el",
        "accordi come suonano",
        "\u05d0\u05e7\u05d5\u05e8\u05d3\u05d9\u05dd \u05e9\u05e0\u05e9\u05de\u05e2\u05d9\u05dd",
    )

    /**
     * The words around the key Campfire's PDF puts in parentheses after a capo whose chords are its shapes, in every
     * language the app prints them in (`song_details_capo_shapes`), as prefix and suffix pairs, lowercase.
     */
    private val shapesCapoMarkers = listOf(
        "" to " shapes",
        "" to " fog\u00e1sok",
        "pozi\u021bii de " to "",
        "" to "-griffe",
        "formas de " to "",
        "doigt\u00e9s de " to "",
        "diteggiature di " to "",
        "\u05e6\u05d5\u05e8\u05d5\u05ea " to "",
    )

    /** `Words and music` in the app's languages besides English, a credit naming who wrote the song. */
    private val foreignCreditLabels = setOf(
        "letra e m\u00fasica", "letra y m\u00fasica", "text und musik", "paroles et musique", "testo e musica", "sz\u00f6veg \u00e9s zene",
        "versuri \u0219i muzic\u0103", "s\u0142owa i muzyka", "text a hudba", "\u05de\u05d9\u05dc\u05d9\u05dd \u05d5\u05dc\u05d7\u05df",
    )

    /** The composer and lyricist labels of the app's languages besides English, which `{composer}` and `{lyricist}` hold. */
    private val composerLabels = setOf(
        "composi\u00e7\u00e3o", "compositor", "komponist", "compositeur", "compositore", "zeneszerz\u0151", "compozitor", "kompozytor",
        "skladatel", "\u05dc\u05d7\u05df",
    )
    private val lyricistLabels = setOf(
        "letrista", "textdichter", "parolier", "paroliere", "sz\u00f6veg\u00edr\u00f3", "textier", "autor tekstu", "texta\u0159",
        "\u05de\u05d9\u05dc\u05d9\u05dd",
    )

    /** The time signature labels of the app's languages besides English and Hungarian, all words a lyric may open with too. */
    private val foreignTimeLabels = setOf("m\u0103sur\u0103", "taktart", "comp\u00e1s", "mesure", "metro", "\u05de\u05e9\u05e7\u05dc")
    private val metadata = Regex(
        "^(Capo|Capotraste|Cejilla|Kapodaster|Kapodastr|Capodastre|Kapo|Capodastru|Capotasto|\u05e7\u05d0\u05e4\u05d5|" +
            "Key|Hangnem|T\u00f3nina|Tom|Tono|Tonalidad|Tonart|Tonalit\u00e9|Tonalit\u00e0|Tonacja|Toonsoort|Tonalitate|\u05d8\u05d5\u05e0\u05dc\u05d9\u05d5\u05ea|" +
            "Tempo|Temp\u00f3|BPM|\u05d8\u05de\u05e4\u05d5|Time signature|Time|\u00dctemmutat\u00f3|M\u0103sur\u0103|Taktart|Comp\u00e1s|Mesure|Metro|\u05de\u05e9\u05e7\u05dc|" +
            "Artist|El\u0151ad\u00f3|Song|Title|Album|Year|Composer|Lyricist|words (?:and|&) music by|words (?:and|&) music|by|" +
            (foreignCreditLabels + composerLabels + lyricistLabels).sortedByDescending { it.length }.joinToString("|") + ")" +
            "\\s*[:;]\\s*(.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val transposition = Regex(
        "^(Transposition|Transzpon\u00e1l\u00e1s|Transpunere|Transposici\u00f3n|Trasposizione|" +
            "\u05d8\u05e8\u05e0\u05e1\u05e4\u05d5\u05d6\u05d9\u05e6\u05d9\u05d4)\\s*:\\s*([+-]?[0-9]+)$",
        RegexOption.IGNORE_CASE,
    )
    private val unlabelledMetadata = Regex(
        "^(Capo|Capotraste|Cejilla|Kapodaster|Kapodastr|Capodastre|Kapo|Capodastru|Capotasto|\u05e7\u05d0\u05e4\u05d5|" +
            "Key of|Key|Hangnem|T\u00f3nina|Tonalidad|Tonart|Tonalit\u00e9|Tonalit\u00e0|Tonacja|Toonsoort|Tonalitate|\u05d8\u05d5\u05e0\u05dc\u05d9\u05d5\u05ea|" +
            "Tempo|Time|words (?:and|&) music by)\\s+(.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val capoPreposition = Regex("^(?:on|at|na|no|en|in|im|am|auf|au|sur|al|pe|\u00e0|\u05e2\u05dc(?:\\s+\u05e9\u05e8\u05d9\u05d2)?|\u05d1\u05e9\u05e8\u05d9\u05d2)\\s+", RegexOption.IGNORE_CASE)
    private val romanNumerals = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")
    private val romanCapo = Regex("^(XII|XI|X|IX|IV|V?I{1,3}|V)(?:\\s+(?:FRET|TRASTE|TASTO|BUND|BUNDSTAB))?$")
    private val noCapo = Regex(
        "^(?:no|sin|senza|nincs|sem|ohne|kein|sans|f\u0103r\u0103|bez)\\s+(?:capo|capotraste|cejilla|capotasto|kapodaster|kapodastr|kapodastru|" +
            "capodastre|capodastru|kapo)$",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A capo's value saying there is none, in the app's languages: a word of none, alone or before the capo's own name
     * (`No capo`, `Sem capotraste`, `Kein Kapodaster`), never any other words, which an unreadable value keeps as text.
     */
    private val noCapoValue = Regex(
        "^(?:no|none|nincs|sin|senza|nessuno|ninguna|ninguno|sem|nenhum|nenhuma|ohne|kein|keiner|aucun|aucune|sans|\u017e\u00e1dn\u00fd|\u017caden|f\u0103r\u0103|fara|brak|bez|\u05dc\u05dc\u05d0|\u05d0\u05d9\u05df|-|\u2013|\u2014)" +
            "(?:\\s+(?:capo|capotraste|cejilla|capotasto|kapodaster|kapodastr|kapodastru|capodastre|capodastru|kapo|\u05e7\u05d0\u05e4\u05d5))?$",
        RegexOption.IGNORE_CASE,
    )

    /** The fields [ChordSheetSiteDetails] reads that a row of labelled values may hold besides the converter's own. */
    private val siteNames = setOf(ChordSheetSiteDetails.TUNING, ChordSheetSiteDetails.DETAIL)
    private val timeSignature = Regex("[0-9]+/[0-9]+")
    internal val timingNames = setOf("tempo", "time")
    private val labelGap = Regex("\\s{2,}")
    private val bpm = Regex("^([0-9]+)\\s+BPM$", RegexOption.IGNORE_CASE)

    /** What [labelledRow] names a transposition, which no directive is written for. */
    internal const val TRANSPOSITION = "transposition"

    /** What [metadata] names a line saying the song is played without a capo, which is the default and is written as nothing. */
    internal const val NO_CAPO = "no capo"

    /** What [metadata] names a capo marked as one the chords are named as they sound over, `2 (chords as they sound)`. */
    internal const val CAPO_SOUNDING = "capo sounding"

    /** What [metadata] names a capo marked with the key of the shapes the chords under it are, `2 (Em shapes)`. */
    internal const val CAPO_SHAPES = "capo shapes"

    /** What [metadata] names a credit (`By:`, `Words and music by:`), whose value may end in the year of the song. */
    internal const val CREDIT = "credit"
}
