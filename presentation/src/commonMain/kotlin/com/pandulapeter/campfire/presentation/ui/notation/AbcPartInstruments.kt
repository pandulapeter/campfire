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

import com.pandulapeter.campfire.data.model.preferences.UserPreferences.Instrument

/**
 * The index of the first of [parts] written for [instrument], by the part's name, or null where none is (and always for
 * [Instrument.OTHER]). Every guitar is the guitar's and every bass the bass's: a score names the instrument, never its
 * strings.
 */
internal fun abcPartFor(parts: List<AbcPart>, instrument: Instrument?): Int? {
    val family = instrument?.family ?: return null
    return parts.indexOfFirst { part -> part.name?.let(::instrumentNamed) == family }.takeIf { it >= 0 }
}

/**
 * The instrument a part's [name] says it is for, in English or the app's other languages, or the abbreviations notation
 * programs write. The bass is asked first, so that a bass guitar is not a guitar.
 */
private fun instrumentNamed(name: String): Instrument? = name.lowercase().let { lowercase ->
    NAMES.firstOrNull { (_, pattern) -> pattern.containsMatchIn(lowercase) }?.first
}

private val Instrument.family
    get() = when (this) {
        Instrument.GUITAR, Instrument.GUITAR_SEVEN_STRING, Instrument.GUITAR_EIGHT_STRING, Instrument.GUITAR_TWELVE_STRING -> Instrument.GUITAR
        Instrument.BASS, Instrument.BASS_FIVE_STRING -> Instrument.BASS
        Instrument.OTHER -> null
        else -> this
    }

private val NAMES = listOf(
    // A bassoon (and the French basson) is no bass.
    Instrument.BASS to Regex("""bass(?!o)|\bbasso\b|bajo|basszus"""),
    Instrument.UKULELE to Regex("""ukulele|ukelele|\buke\b"""),
    Instrument.BANJO to Regex("""banjo"""),
    Instrument.MANDOLIN to Regex("""mandolin"""),
    // A violoncello is no violin.
    Instrument.VIOLIN to Regex("""violin|fiddle|violon(?!c)|geige|hegedű|vioară|כינור|\bvln?\b"""),
    Instrument.GUITAR to Regex("""guitar|gitar|gitár|chitarr|גיטרה|\bgtr\b"""),
    Instrument.KEYBOARD to Regex("""piano|keyboard|\bkeys\b|klavier|zongora|organ|orgel|orgue|órgano|orgona|synth|rhodes|clavi|cembalo|harpsichord|פסנתר"""),
)
