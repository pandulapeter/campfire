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

import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.chordTokens

/**
 * The song details a chord site prints around a song besides its key, capo, and tempo (the tuning, the difficulty, who
 * transcribed it, a CCLI number), and the controls and calls to action a copy of its page carries along ("Check out the
 * tab", "Autoscroll", "1,234 views"), read by their labels and words in every language the app ships, never by a site's
 * name. A copy read with the elements' boundaries lost glues a details block into one line, each label onto the value
 * before it (`Tuning: E A D G B EKey: GCapo: No capo`), which [parts] cuts back apart.
 */
internal object ChordSheetSiteDetails {

    /** What [detail] names a tuning, written as `{meta: tuning …}` unless it is the standard one ([isStandardTuning]). */
    const val TUNING = "tuning"

    /** What [detail] names a song's CCLI number, written as `{meta: ccli …}`. */
    const val CCLI = "ccli"

    /** What [detail] names a value that is the site's and not the song's (a difficulty, a transcriber), left out. */
    const val DETAIL = "detail"

    /**
     * The labelled values a line holds one after another, where it opens with the label of a song's details: cut before
     * each further such label followed by a colon, however little stands between (nothing, where the copy glued them).
     * The labels it cuts at are the capitalized ones sites print, so a value's lowercase word is never cut at. Any other
     * line is one part.
     */
    fun parts(text: String): List<String> {
        val trimmed = text.trim()
        if (!rowStart.containsMatchIn(trimmed)) return listOf(trimmed)
        val starts = rowLabel.findAll(trimmed).map { it.range.first }.filter { it > 0 }.toList()
        return (listOf(0) + starts).zip(starts + trimmed.length) { from, until -> trimmed.substring(from, until).trim() }
    }

    /**
     * The field a line of one labelled value names that the converter's own fields do not: [TUNING], [CCLI], or [DETAIL];
     * null for any other line. A label needs its colon, and only a transcriber's credit that says it is one (counting its
     * contributors or naming its last edit) is a detail: an `Author:` alone is as likely the song's.
     */
    fun detail(text: String): Pair<String, String>? {
        val trimmed = text.trim()
        ccliSong.matchEntire(trimmed)?.let { return CCLI to it.groupValues[1] }
        if (ccliLicence.matches(trimmed)) return DETAIL to trimmed
        val match = labelled.matchEntire(trimmed) ?: return null
        val label = match.groupValues[1].lowercase()
        val value = match.groupValues[2].trim()
        return when (label) {
            in tuningLabels -> TUNING to value
            in detailLabels -> DETAIL to value
            in authorLabels -> (DETAIL to value).takeIf { transcriberWords.containsMatchIn(value) }
            else -> null
        }
    }

    /** Whether a tuning [value] names the standard one (`E A D G B E`, `Standard`, `Padrão (E A D G B E)`), written as nothing. */
    fun isStandardTuning(value: String): Boolean {
        val words = value.lowercase().replace('(', ' ').replace(')', ' ').split(' ', ',').filter { it.isNotEmpty() }
        val notes = words.filter { it !in standardWords }
        return notes.size < words.size && notes.isEmpty() || notes.joinToString("") == "eadgbe"
    }

    /**
     * The [lines] of a song without the site's chrome: its calls to action and controls ([isCallToAction]), and the
     * values that are the site's ([DETAIL]), where they stand before the song's first chord row, after its last, or
     * alone between blank lines. Between two lyric lines a line of the same words is as likely sung, so it stays.
     */
    fun withoutChrome(lines: List<Rendered>): List<Rendered> {
        val chordRows = lines.indices.filter { lines[it].text.isNotBlank() && chordTokens(lines[it]) != null }
        val first = chordRows.firstOrNull() ?: return lines
        val last = chordRows.last()
        fun isBlank(index: Int) = lines.getOrNull(index)?.text?.isBlank() ?: true
        return lines.filterIndexed { index, line ->
            val isChrome = isCallToAction(line.text) || ChordSheetMetadata.metadata(line.text)?.first == DETAIL
            !isChrome || index in first..last && !(isBlank(index - 1) && isBlank(index + 1))
        }
    }

    /** Whether [text] is, as a whole line, one of a chord site's controls or calls to action. */
    fun isCallToAction(text: String): Boolean {
        val trimmed = text.trim().trimEnd('»', '›', '>', '→', ' ').lowercase()
        return trimmed in callsToAction || chromePatterns.any { it.matches(trimmed) }
    }

    private val tuningLabels = setOf(
        "tuning", "afinação", "afinación", "stimmung", "accordage", "accordatura", "hangolás", "acordaj", "strój",
        "strojenie", "ladění", "כיוונון",
    )
    private val detailLabels = setOf(
        "difficulty", "rating", "views", "last update", "last updated", "last edit", "speed", "autoscroll", "tabbed by",
        "transcribed by", "submitted by", "contributed by", "uploaded by", "dificuldade", "colaboração e revisão",
        "dificultad", "schwierigkeit", "schwierigkeitsgrad", "difficulté", "difficoltà", "nehézség", "dificultate",
        "trudność", "poziom trudności", "obtížnost", "רמת קושי",
    )
    private val authorLabels = setOf("author", "autor", "auteur", "autore", "szerző")
    private val transcriberWords = Regex("contributors?\\b|last edit", RegexOption.IGNORE_CASE)
    private val standardWords = setOf(
        "standard", "tuning", "padrão", "afinação", "estándar", "normal", "normale", "normál", "standardowy",
        "standardowe", "standardní", "normální", "סטנדרטי", "רגיל",
    )

    /** The labels of a song's details in the case sites print them, longest first so a longer one is not cut short. */
    private val rowLabels = listOf(
        "Time signature", "Key", "Capo", "Tuning", "Tempo", "BPM", "Time", "Difficulty", "Tom", "Tono", "Tonalidad", "Tonart",
        "Tonalité", "Tonalità", "Tonacja", "Tonalitate", "Hangnem", "Tónina", "Capotraste", "Cejilla", "Kapodaster",
        "Kapodastr", "Capodastre", "Capodastru", "Capotasto", "Afinação", "Afinación", "Stimmung", "Accordage",
        "Accordatura", "Hangolás", "Acordaj", "Strój", "Ladění", "Dificuldade", "Dificultad", "Schwierigkeit",
        "Difficulté", "Difficoltà", "Nehézség", "Dificultate", "Trudność", "Obtížnost",
        "קאפו", "טונליות", "טמפו", "משקל",
        "כיוונון",
    ).sortedByDescending { it.length }.joinToString("|")
    private val rowLabel = Regex("(?:$rowLabels)\\s*:")
    private val rowStart = Regex("^(?:$rowLabels)\\s*:", RegexOption.IGNORE_CASE)
    private val labelled = Regex(
        "^(" + (tuningLabels + detailLabels + authorLabels).sortedByDescending { it.length }.joinToString("|") + ")\\s*:\\s*(.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val ccliSong = Regex("^CCLI\\s+Song\\s*(?:#|No\\.?|Number)\\s*:?\\s*([0-9]+)$", RegexOption.IGNORE_CASE)
    private val ccliLicence = Regex("^CCLI\\s+Licen[cs]e\\s*(?:#|No\\.?|Number)\\s*:?\\s*[0-9]+$", RegexOption.IGNORE_CASE)

    /** Whole lines, lowercase and without a closing arrow, that are a site's controls or calls to action. */
    private val callsToAction = setOf(
        "check out the tab", "view official tab", "report bad tab", "autoscroll", "auto scroll", "transpose", "simplify",
        "add to playlist", "rate this tab", "please, rate this tab", "create and get +5 iq",
        "there is no strumming pattern for this song yet. create and get +5 iq", "ad loading…", "ad loading...",
        "simplificar cifra", "auto rolagem", "exibir cifra", "www.ccli.com",
    )
    private val chromePatterns = listOf(
        // A count of views, as many sites print one under the title: `1,234,567 views, added to favorites 3,210 times`.
        Regex(
            "^[0-9][0-9.,  ]*[km]?\\s+(?:views?|visualizações|exibições|acessos|visitas|aufrufe|vues|" +
                "visualizzazioni|megtekintés|vizualizări|wyświetle\\p{L}*|zobrazení|צפיות)\\b.*$",
        ),
        // The song's length a site prints beside its controls, `4 min`.
        Regex("^[0-9]+\\s*min$"),
        Regex("^[0-9]+ contributors? total\\b.*$"),
        Regex("^last (?:edit|update)(?:d|ed)?(?: on)?\\s*:?\\s*.*[0-9].*$"),
        Regex("^for use solely with the songselect.*$"),
    )
}
