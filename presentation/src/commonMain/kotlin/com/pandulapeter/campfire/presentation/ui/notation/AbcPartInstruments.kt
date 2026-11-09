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
 *
 * A choir's voices are nobody's, though one is named like an instrument: a staff several named voices share ("Tenor /
 * Bass") is a choir's, and so is a part named only as a voice ("Bass", "Bass 2") in a score naming another part a
 * singing voice (a Soprano, an Alto, a Tenor). A band's "Bass" beside its "Vocals" is still the bass player's.
 */
internal fun abcPartFor(parts: List<AbcPart>, instrument: Instrument?): Int? {
    val family = instrument?.family ?: return null
    val hasChoir = parts.any { part ->
        part.name.orEmpty().split(SHARED_STAFF_NAME_SEPARATOR).any { SINGING_VOICE.matches(it.trim().lowercase()) }
    }
    return parts.indexOfFirst { part ->
        val name = part.name ?: return@indexOfFirst false
        val isChoirVoice = SHARED_STAFF_NAME_SEPARATOR in name || (hasChoir && CHOIR_BASS.matches(name.trim().lowercase()))
        !isChoirVoice && instrumentNamed(name) == family
    }.takeIf { it >= 0 }
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

/** A part named only as one of a choir's voices but the bass (the bass's name is an instrument's too), numbered or not. */
private val SINGING_VOICE = Regex("""(?:soprano|sopran|szoprán|mezzo-?soprano|mezzo|alto|alt|contralto|tenor|ténor|tenore|baritone|bariton|baritono|barítono)s?\.?\s*\d*""")

/** A part named only as a choir's bass, numbered or not (`Bass 2`, `Basses`). */
private val CHOIR_BASS = Regex("""(?:bass|basse|basso|bassi|bajo|bajos|bas|basszus)(?:es)?\.?\s*\d*""")

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
