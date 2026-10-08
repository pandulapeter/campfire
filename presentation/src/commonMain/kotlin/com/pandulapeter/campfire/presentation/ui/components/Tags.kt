/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.data.model.domain.Tag
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.model.domain.isCombiningMark
import com.pandulapeter.campfire.data.model.domain.withoutAccent

/**
 * The order the tags and the languages of one song are shown in: alphabetical by what the chip reads, without regard
 * to case or accents, so that an `Ő` is among the `O`s rather than after `Z` and `blues` does not follow `Rock` only
 * for having been typed in lowercase. The file's own order is somebody's typing order, which says nothing a reader
 * looking for one label among many can use.
 */
internal fun <T> List<T>.sortedAlphabeticallyBy(label: (T) -> String): List<T> =
    sortedWith(compareBy<T> { alphabeticalKey(label(it)) }.thenBy(label))

/**
 * The library's tags in the order the user picked for them. They arrive most used first (`ScreenData.tags`), so only
 * the alphabet has anything to do.
 */
internal fun List<Tag>.orderedBy(sortingMode: UserPreferences.LabelSortingMode) = when (sortingMode) {
    UserPreferences.LabelSortingMode.BY_USAGE -> this
    UserPreferences.LabelSortingMode.ALPHABETICAL -> sortedAlphabeticallyBy { it.name }
}

/**
 * The library's languages in the order the user picked for them, alphabetical by [label], which is the name the
 * platform gives each one in the language the app is set to. [SongLanguage.UNKNOWN] stays last either way: it is not
 * a language, and "Unknown" among the U's would read as one.
 */
internal fun List<SongLanguage>.orderedBy(
    sortingMode: UserPreferences.LabelSortingMode,
    label: (code: String) -> String,
) = when (sortingMode) {
    UserPreferences.LabelSortingMode.BY_USAGE -> this
    UserPreferences.LabelSortingMode.ALPHABETICAL -> partition { it.code != SongLanguage.UNKNOWN }.let { (languages, unknown) ->
        languages.sortedAlphabeticallyBy { label(it.code) } + unknown
    }
}

private fun alphabeticalKey(text: String) = buildString(text.length) {
    text.lowercase().forEach { character -> if (!character.isCombiningMark()) append(character.withoutAccent()) }
}

/** The gap between two tags, horizontally and between the rows they wrap onto. */
internal val TAG_GAP = 4.dp
