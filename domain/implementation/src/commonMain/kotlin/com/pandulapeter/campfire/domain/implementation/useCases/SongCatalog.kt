/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.data.model.domain.Tag
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.models.SongFilter

/**
 * Tags are matched without regard to case, here and everywhere else, so both sides are folded to lower case
 * before they meet. A selected tag no song carries any more is dropped instead of emptying the list: it is kept
 * in the filter on purpose, see [SongFilter.selectedTags].
 */
internal fun List<Song>.filterTags(songFilter: SongFilter, matchMode: UserPreferences.MatchMode, tags: List<Tag>): List<Song> {
    val available = tags.mapTo(mutableSetOf()) { it.name.lowercase() }
    val selected = songFilter.selectedTags.map { it.lowercase() }.filter { it in available }
    if (selected.isEmpty()) return this
    return filter { song ->
        val songTags = song.tags.mapTo(mutableSetOf()) { it.lowercase() }
        when (matchMode) {
            UserPreferences.MatchMode.ANY -> selected.any { it in songTags }
            UserPreferences.MatchMode.ALL -> selected.all { it in songTags }
        }
    }
}

/**
 * The songs left by the language filter. A song carries its languages the way it carries its tags, so several
 * selected languages combine the way several tags do; [SongLanguage.UNKNOWN] selects the songs that declare none,
 * which no song can name itself, see [SongLanguage.Companion.UNKNOWN]. Such a song is taken to be in that one
 * "language", so asking for every one of several selected languages never finds it next to a named one.
 */
internal fun List<Song>.filterLanguages(songFilter: SongFilter, matchMode: UserPreferences.MatchMode, languages: List<SongLanguage>): List<Song> {
    val available = languages.mapTo(mutableSetOf()) { it.code }
    val selected = songFilter.selectedLanguages.filter { it in available }
    if (selected.isEmpty()) return this
    return filter { song ->
        val songLanguages = song.languages.ifEmpty { listOf(SongLanguage.UNKNOWN) }
        when (matchMode) {
            UserPreferences.MatchMode.ANY -> selected.any { it in songLanguages }
            UserPreferences.MatchMode.ALL -> selected.all { it in songLanguages }
        }
    }
}

/**
 * Every tag of the library as the filter controls show it, counted over [songs] - the ones the language filter
 * leaves. A tag the language filter has counted down to nothing stays on the list with a zero rather than
 * dropping off it: the chips would otherwise come and go under the user's finger as the other group changes, and
 * a zero says exactly what it means - this combination of the two groups selects nothing. The zeros sort last,
 * which keeps the tags that still do something among the ones shown before "show all".
 */
internal fun List<Tag>.recountedTagsOver(songs: List<Song>, normalize: (String) -> String): List<Tag> {
    val counts = mutableMapOf<String, Int>()
    songs.forEach { song ->
        song.tags.mapTo(mutableSetOf()) { it.lowercase() }.forEach { tag -> counts[tag] = (counts[tag] ?: 0) + 1 }
    }
    return map { it.copy(songCount = counts[it.name.lowercase()] ?: 0) }.sortedWith(tagOrder(normalize))
}

/** Every language of the library counted over [songs] - the ones the tag filter leaves - the way [recountedTagsOver] counts the tags. */
internal fun List<SongLanguage>.recountedLanguagesOver(songs: List<Song>): List<SongLanguage> {
    val counts = songs.toLanguages().associate { it.code to it.songCount }
    return map { it.copy(songCount = counts[it.code] ?: 0) }.sortedWith(languageOrder)
}

/**
 * The languages of the library with the number of songs singing in each, most used first, and the songs that
 * declare none after them however many they are: "unknown" is where the work that is still to be done sits,
 * not a language competing with the rest for the top of the list.
 */
internal fun List<Song>.toLanguages(): List<SongLanguage> {
    val countsByCode = linkedMapOf<String, Int>()
    forEach { song ->
        if (song.languages.isEmpty()) {
            countsByCode[SongLanguage.UNKNOWN] = (countsByCode[SongLanguage.UNKNOWN] ?: 0) + 1
        } else {
            song.languages.forEach { code -> countsByCode[code] = (countsByCode[code] ?: 0) + 1 }
        }
    }
    return countsByCode
        .map { (code, songCount) -> SongLanguage(code = code, songCount = songCount) }
        .sortedWith(languageOrder)
}

/**
 * The tags of the library with the number of songs carrying each, most used first. Two spellings of the same word
 * are one tag, shown the way the song that comes first by file name spells it - by file name rather than by where
 * the song is in the list, because the repository's list is in no particular order (a song that was just saved is
 * at its end), and the chip would change its capitals after an edit to a song and back after the next rescan.
 */
internal fun List<Song>.toTags(normalize: (String) -> String): List<Tag> {
    val tagsByName = linkedMapOf<String, SpelledTag>()
    forEach { song ->
        song.tags.forEach { tag ->
            val spelled = tagsByName.getOrPut(tag.lowercase()) { SpelledTag(name = tag, fileName = song.fileName) }
            spelled.songCount++
            if (song.fileName < spelled.fileName) {
                spelled.name = tag
                spelled.fileName = song.fileName
            }
        }
    }
    return tagsByName.values
        .map { Tag(name = it.name, songCount = it.songCount) }
        .sortedWith(tagOrder(normalize))
}

/** Most used first, and the songs that declare none last, see [toLanguages]. */
private val languageOrder = compareBy<SongLanguage> { it.code == SongLanguage.UNKNOWN }.thenByDescending { it.songCount }.thenBy { it.code }

/** Most used first. Tags fold case but not accents, so two of them can share the text they are sorted by. */
private fun tagOrder(normalize: (String) -> String) =
    compareByDescending<Tag> { it.songCount }.thenBy { normalize(it.name) }.thenBy { it.name }

/**
 * A tag while it is being counted.
 *
 * @param fileName The song [name] is spelled after: the first by file name of the ones counted so far.
 */
private class SpelledTag(
    var name: String,
    var fileName: String,
    var songCount: Int = 0,
)
