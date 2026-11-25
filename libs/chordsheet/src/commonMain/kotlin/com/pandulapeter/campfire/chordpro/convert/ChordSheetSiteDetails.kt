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
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.chordTokens
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.isChordOrLatinName

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

    /**
     * The [DETAIL] a part of a details row names where it is an author's credit (`Author: someone [pro] 52`): beside a
     * value that is the site's, such as a difficulty, it credits the site's transcriber rather than the song's author.
     * Null for any other part.
     */
    fun transcriber(part: String): Pair<String, String>? =
        labelled.matchEntire(part.trim())?.takeIf { it.groupValues[1].lowercase() in authorLabels }?.let { DETAIL to it.groupValues[2].trim() }

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

    /**
     * The [lines] with a details row whose last label has no value (`Tuning: E A D G B ECapo:`) joined to the line right
     * under it where that line is only a value of the label's field (`2nd fret`), as a copy that broke the line between
     * the label's element and its value's leaves it; the row is then read whole ([ChordSheetMetadata.labelledRow]).
     * A capo, key, tempo, or time signature only, each by its own shape alone, so a sung line under a bare label stays.
     */
    fun withValuesJoined(lines: List<Rendered>): List<Rendered> {
        val joined = mutableListOf<Rendered>()
        var index = 0
        while (index < lines.size) {
            val row = lines[index]
            val value = lines.getOrNull(index + 1)
            if (value != null && isValueOfLastLabel(row.text, value.text)) {
                val text = value.text.trim()
                val start = value.text.indexOf(text)
                val space = (row.positions.lastOrNull() ?: 0.0) + 1.0
                joined += row.copy(
                    text = "${row.text} $text",
                    positions = row.positions + space + value.positions.copyOfRange(start, start + text.length),
                    bidi = null,
                )
                index += 2
            } else {
                joined += row
                index++
            }
        }
        return joined
    }

    private fun isValueOfLastLabel(row: String, value: String): Boolean {
        val trimmed = row.trim()
        val label = emptyLabel.find(trimmed)?.groupValues?.get(1)?.takeIf { rowStart.containsMatchIn(trimmed) } ?: return false
        val candidate = value.trim()
        val isValue = when (ChordSheetMetadata.metadata("$label: $candidate")?.first) {
            "capo" -> capoValue.matches(candidate.lowercase()) || romanCapoValue.matches(candidate)
            ChordSheetMetadata.NO_CAPO -> true
            "key" -> isChordOrLatinName(ascii(candidate))
            "tempo" -> tempoValue.matches(candidate)
            "time" -> timeValue.matches(candidate)
            else -> false
        }
        return isValue && ChordSheetMetadata.labelledRow("$trimmed $candidate", isHeader = true) != null
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
        "difficulty", "rating", "views", "last update", "last updated", "last edit", "speed", "autoscroll", "dificuldade",
        "colaboração e revisão", "dificultad", "schwierigkeit", "schwierigkeitsgrad", "difficulté", "difficoltà", "nehézség",
        "dificultate", "trudność", "poziom trudności", "obtížnost", "רמת קושי",
        // Who wrote the tab or the chart down for the site, which no song file of its own credits.
        "tabbed", "tabbed by", "tab by", "tabs by", "transcribed", "transcribed by", "transcription", "transcription by",
        "transcriber", "submitted by", "contributed by", "uploaded by", "transkription", "transkribiert von", "transcrit par",
        "transcripción", "transcrito por", "transcrição", "trascrizione", "trascritto da", "lejegyezte", "lejegyzés",
        "transcriere", "transcris de", "transkrypcja", "przepisał", "přepis", "přepsal", "transkripce", "הועלה על ידי",
        "תומלל על ידי",
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
        "Difficulté", "Difficoltà", "Nehézség", "Dificultate", "Trudność", "Obtížnost", "Author", "Autor", "Auteur", "Autore",
        "Szerző",
        "קאפו", "טונליות", "טמפו", "משקל",
        "כיוונון",
    ).sortedByDescending { it.length }.joinToString("|")
    private val rowLabel = Regex("(?:$rowLabels)\\s*:")
    private val rowStart = Regex("^(?:$rowLabels)\\s*:", RegexOption.IGNORE_CASE)
    private val emptyLabel = Regex("($rowLabels)\\s*:$")
    private val capoValue = Regex("^[0-9]{1,2}(?:st|nd|rd|th|\\.|ª|º|°)?(?:\\s+(?:frets?|traste|casa|tasto|bund|case|tastă|próg|pražec|שריג))?$")
    private val romanCapoValue = Regex("^(?:XII|XI|X|IX|IV|V?I{1,3}|V)$")
    private val tempoValue = Regex("^[0-9]{2,3}(?:\\s*BPM)?$", RegexOption.IGNORE_CASE)
    private val timeValue = Regex("^[0-9]{1,2}/[0-9]{1,2}$")
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
        "simplificar cifra", "auto rolagem", "exibir cifra", "www.ccli.com", "interactive tab",
        // A button saving the song to the visitor's account, in every language the app ships.
        "save", "mentés", "salvează", "speichern", "guardar", "enregistrer", "salva", "שמור", "salvar", "zapisz", "uložit",
    )
    private val chromePatterns = listOf(
        // A count of views or saves, as many sites print one under the title: `1,234,567 views, added to favorites 3,210
        // times`, the next count glued to it where the copy lost the space between them (`6,799 views201 saves`).
        Regex(
            "^[0-9][0-9.,  ]*[km]?\\s+(?:views?|saves?|visualizações|exibições|acessos|visitas|aufrufe|vues|" +
                "visualizzazioni|megtekintés|vizualizări|wyświetle\\p{L}*|zobrazení|צפיות)(?!\\p{L}).*$",
        ),
        // The song's length a site prints beside its controls, `4 min`.
        Regex("^[0-9]+\\s*min$"),
        Regex("^[0-9]+ contributors? total\\b.*$"),
        Regex("^last (?:edit|update)(?:d|ed)?(?: on)?\\s*:?\\s*.*[0-9].*$"),
        Regex("^for use solely with the songselect.*$"),
    )
}
