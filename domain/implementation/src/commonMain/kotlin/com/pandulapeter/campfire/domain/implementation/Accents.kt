/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation

/**
 * The lowercase Latin letter behind an accented one, or the character itself when there is none. Shared by the text
 * normalization behind sorting and searching and by the naming of exported files, which both need a song title to
 * come out as the plain letters someone would have typed looking for it.
 *
 * A `when` over the characters rather than a map, so that the lookup doesn't box a Char per character of a song, and
 * the Hungarian and Romanian letters the app was written around come before the rest of the Latin accents a song
 * title is likely to carry — so that "Édith" sorts next to "Edith" whichever language the artist sings in.
 *
 * Anything outside this table is left alone: Kotlin's common standard library has no Unicode normalizer, so this is
 * the whole of what the app knows about accents.
 */
internal fun Char.withoutAccent() = when (this) {
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
