/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

/**
 * The lowercase Latin letter behind an accented one, or the character itself when there is none. Shared by the text
 * normalization behind sorting and searching (`:domain:implementation`) and by [LibraryFiles.normalizedName], which
 * both need a song title to come out as the plain letters someone would have typed looking for it.
 *
 * The table is every lowercase letter of Latin-1, Latin Extended-A and -B and Latin Extended Additional that is a
 * plain letter with marks on it, plus the letters drawn with a stroke or a bar, which Unicode does not decompose but
 * which a keyboard without them spells with the bare letter all the same (`ł`, `đ`, the Turkish dotless `ı`). A
 * letter left out of it is not kept either: `normalizedName` turns it into a separator, which files a Polish or a
 * Vietnamese title under a name with holes in it, and under a different name from the same title written decomposed.
 *
 * Only lowercase letters are listed, since both callers lowercase first. A string per letter rather than a map, so
 * that nothing boxes a Char, and everything below the first accented letter returns at once, which is almost every
 * character of almost every song.
 *
 * Anything outside this table is left alone: Kotlin's common standard library has no Unicode normalizer, so this is
 * the whole of what the app knows about accents. The letters that fold to two ("ß", "æ", "þ"…) are the file name's
 * business, see [LibraryFiles.normalizedName].
 */
fun Char.withoutAccent() = when {
    this < FIRST_ACCENTED_LETTER -> this
    this in "àáâãäåāăąǎǟǡǻȁȃȧḁạảấầẩẫậắằẳẵặẚ" -> 'a'
    this in "ƀḃḅḇ" -> 'b'
    this in "çćĉċčḉ" -> 'c'
    this in "ďđðḋḍḏḑḓ" -> 'd'
    this in "èéêëēĕėęěȅȇȩḕḗḙḛḝẹẻẽếềểễệ" -> 'e'
    this in "ḟ" -> 'f'
    this in "ĝğġģǥǧǵḡ" -> 'g'
    this in "ĥħȟḣḥḧḩḫẖ" -> 'h'
    this in "ìíîïĩīĭįıǐȉȋḭḯỉị" -> 'i'
    this in "ĵǰȷ" -> 'j'
    this in "ķǩḱḳḵ" -> 'k'
    this in "ĺļľŀłḷḹḻḽ" -> 'l'
    this in "ḿṁṃ" -> 'm'
    this in "ñńņňǹṅṇṉṋ" -> 'n'
    this in "òóôõöøōŏőơǒǫǭǿȍȏȫȭȯȱṍṏṑṓọỏốồổỗộớờởỡợ" -> 'o'
    this in "ṕṗ" -> 'p'
    this in "ŕŗřȑȓṙṛṝṟ" -> 'r'
    this in "śŝşšșſṡṣṥṧṩẛ" -> 's'
    this in "ţťțŧṫṭṯṱẗ" -> 't'
    this in "ùúûüũūŭůűųưǔǖǘǚǜȕȗṳṵṷṹṻụủứừửữự" -> 'u'
    this in "ṽṿ" -> 'v'
    this in "ŵẁẃẅẇẉẘ" -> 'w'
    this in "ẋẍ" -> 'x'
    this in "ýÿŷȳẏẙỳỵỷỹ" -> 'y'
    this in "źżžƶẑẓẕ" -> 'z'
    else -> this
}

/** À, the first accented letter of Latin-1: nothing below it has an accent to lose. */
private const val FIRST_ACCENTED_LETTER = '\u00C0'

/**
 * A combining mark of the block that carries every Latin accent. A title can arrive decomposed - an "é" written as "e"
 * followed by U+0301, which is what some tools and macOS paths produce - and there is no Unicode normalizer in common
 * Kotlin to compose it back. Dropping the mark once the base letter has been folded brings both spellings to the same
 * plain letters, so the same title neither files under two names nor fails to find itself.
 */
fun Char.isCombiningMark() = this in '\u0300'..'\u036F'
