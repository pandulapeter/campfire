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

import com.pandulapeter.campfire.domain.api.useCases.NormalizeTextUseCase

class NormalizeTextUseCaseImpl internal constructor() : NormalizeTextUseCase {

    /**
     * Sorting, grouping and searching all run this over every song, so it walks the text once and only allocates a
     * new string when there is actually an accent to replace - a chain of [String.replace] calls allocated one for
     * every accent the app knows about, whether the text contained it or not.
     */
    override fun invoke(text: String): String {
        val lowercase = text.trim().lowercase()
        var builder: StringBuilder? = null
        for (index in lowercase.indices) {
            val character = lowercase[index]
            val replacement = character.withoutAccent()
            if (replacement == character) {
                builder?.append(character)
            } else {
                if (builder == null) {
                    builder = StringBuilder(lowercase.length).append(lowercase, 0, index)
                }
                builder.append(replacement)
            }
        }
        return builder?.toString() ?: lowercase
    }

    // A `when` over the characters rather than a map, so that the lookup doesn't box a Char per character of a song.
    // The Hungarian and Romanian letters the app was written around, plus the rest of the Latin accents a song title
    // is likely to carry, so that "Édith" sorts next to "Edith" whichever language the artist sings in.
    private fun Char.withoutAccent() = when (this) {
        'á', 'à', 'â', 'ä', 'ã', 'å', 'ă', 'ā' -> 'a'
        'é', 'è', 'ê', 'ë', 'ě', 'ē' -> 'e'
        'í', 'ì', 'î', 'ï', 'ī' -> 'i'
        'ó', 'ò', 'ô', 'ö', 'ő', 'õ', 'ø', 'ō' -> 'o'
        'ú', 'ù', 'û', 'ü', 'ű', 'ů', 'ū' -> 'u'
        'ý', 'ÿ' -> 'y'
        'ç', 'č', 'ć' -> 'c'
        'ñ', 'ň' -> 'n'
        'ș', 'š', 'ś' -> 's'
        'ț', 'ť' -> 't'
        'ž', 'ź', 'ż' -> 'z'
        'ď' -> 'd'
        'ř' -> 'r'
        'ł' -> 'l'
        else -> this
    }
}
