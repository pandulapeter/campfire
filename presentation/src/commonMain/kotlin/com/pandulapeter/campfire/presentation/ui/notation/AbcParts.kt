/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

/**
 * One instrument's part of a score: the staves one player reads, which is a single voice, a grand staff (`{2 | 3}`),
 * voices sharing a staff (`(1 2)`), or neighbouring staves of one name (a notation program's guitar staff and its
 * tablature, both `nm="Guitar"`).
 *
 * @param name The `nm=` (or `name=`) of its first named voice, null where none is named.
 * @param voiceId The id of its first voice, which names a part that has no name.
 * @param score What a `%%score` line lists to engrave this part alone.
 */
internal data class AbcPart(
    val name: String?,
    val voiceId: String,
    val score: String,
)

/**
 * The parts [tune] can be shown one at a time, in score order: those its `%%score` / `%%staves` line lists (a bracket
 * only groups instruments, so each of its members is a part), or else each voice it defines. Empty where there is
 * nothing to choose between, a single part, or where the text holds several tunes, whose voices are their own.
 */
internal fun abcPartsOf(tune: String): List<AbcPart> {
    if (TUNE_START.findAll(tune).count() > 1) return emptyList()
    val names = LinkedHashMap<String, String?>()
    VOICE_LINE.findAll(tune).forEach { match ->
        val id = match.groupValues[1]
        val name = VOICE_NAME.find(match.groupValues[2])?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }?.trim()?.takeIf { it.isNotEmpty() }
        if (names[id] == null) names[id] = name
    }
    val items = SCORE_LINE.find(tune)?.let { scoreItems(tokensOf(it.groupValues[1])) } ?: names.keys.map { ScoreItem(listOf(it), it) }
    val parts = mutableListOf<AbcPart>()
    items.forEach { item ->
        // Voices sharing a staff are named together, as abc2svg names the staff ("Soprano / Alto").
        val name = item.voiceIds.mapNotNull { names[it] }.distinct().joinToString(separator = " / ").ifEmpty { null }
        val previous = parts.lastOrNull()
        if (previous != null && name != null && previous.name.equals(name, ignoreCase = true)) {
            parts[parts.lastIndex] = previous.copy(score = "${previous.score} ${item.score}")
        } else {
            parts += AbcPart(name = name, voiceId = item.voiceIds.first(), score = item.score)
        }
    }
    return parts.takeIf { it.size > 1 }.orEmpty()
}

/**
 * [tune] engraving only [part]: every score line it has is replaced by one listing the part, and the music of every
 * other voice is left out too. A voice the score leaves out still keeps time for abc2svg, which pads the voices shown to
 * the longest: an export whose voices end a fraction of a bar apart gets a rest of a fraction of a bar, counted in
 * digits no music glyph draws, and the tune would stay text. A `V:` line, or a line's inline `[V:…]` fields, start a
 * voice's music, which runs on until the next; a line naming voices both shown and not is kept, and what comes before the
 * first voice is kept too.
 */
internal fun abcTuneWithPart(tune: String, part: AbcPart): String {
    val voiceIds = tokensOf(part.score).filter { it !in SCORE_PUNCTUATION }.toSet()
    val lines = tune.lines().filterNot { SCORE_LINE.matches(it) }
    val bodyStart = lines.indexOfFirst { it.startsWith("K:") } + 1
    var voice: String? = null
    val kept = lines.filterIndexed { index, line ->
        if (index < bodyStart || bodyStart == 0) return@filterIndexed true
        VOICE_LINE.matchEntire(line)?.let { voice = it.groupValues[1] }
        val inlineVoices = INLINE_VOICE.findAll(line).map { it.groupValues[1] }.toList()
        if (inlineVoices.isNotEmpty()) {
            voice = inlineVoices.last()
            return@filterIndexed inlineVoices.any { it in voiceIds }
        }
        voice == null || voice in voiceIds
    }
    val tuneStart = kept.indexOfFirst { it.startsWith("X:") }
    return (kept.take(tuneStart + 1) + "%%score ${part.score}" + kept.drop(tuneStart + 1)).joinToString(separator = "\n")
}

private class ScoreItem(val voiceIds: List<String>, val score: String)

private fun tokensOf(score: String) = SCORE_TOKEN.findAll(score).map { it.value }.toList()

/**
 * The items of a score line at its top level. A brace (one instrument's staves) and a parenthesis (voices on one staff)
 * are an item each, kept as written; a bracket only groups instruments, so its members are items of their own, and a bar
 * line between staves joins nothing a part needs.
 */
private fun scoreItems(tokens: List<String>): List<ScoreItem> {
    val items = mutableListOf<ScoreItem>()
    var index = 0
    while (index < tokens.size) {
        val token = tokens[index]
        when (token) {
            "{", "(" -> {
                var depth = 0
                val group = mutableListOf<String>()
                while (index < tokens.size) {
                    val inner = tokens[index]
                    group += inner
                    if (inner == "{" || inner == "(") depth++
                    if (inner == "}" || inner == ")") depth--
                    if (depth == 0) break
                    index++
                }
                val voiceIds = group.filter { it !in SCORE_PUNCTUATION }
                if (voiceIds.isNotEmpty()) items += ScoreItem(voiceIds, group.joinToString(separator = " "))
            }

            in SCORE_PUNCTUATION -> Unit
            else -> items += ScoreItem(listOf(token), token)
        }
        index++
    }
    return items
}

private val SCORE_PUNCTUATION = setOf("{", "}", "[", "]", "(", ")", "|")
private val SCORE_TOKEN = Regex("""[{}\[\]()|]|[^\s{}\[\]()|*]+""")
private val SCORE_LINE = Regex("""(?m)^(?:%%|I:)(?:score|staves)\b(.*)$""")
private val TUNE_START = Regex("""(?m)^X:""")
private val VOICE_LINE = Regex("""(?m)^V:[ \t]*([^\s\]]+)(.*)$""")
private val INLINE_VOICE = Regex("""\[V:[ \t]*([^\s\]]+)[^\]]*\]""")
private val VOICE_NAME = Regex("""(?:^|\s)(?:nm|name)\s*=\s*(?:"([^"]*)"|(\S+))""")
