/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tabs

import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.tabs.ChordProTabs
import com.pandulapeter.campfire.chordpro.model.TabPlayer
import com.pandulapeter.campfire.chordpro.model.TabTuning
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.instrument.isTwelveString
import com.pandulapeter.campfire.presentation.ui.instrument.tunings
import com.pandulapeter.campfire.presentation.ui.tuner.noteName
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * Who the tabs of a song are read by: the player of [instrument], tuned to [tuning], reading chords in [notation].
 *
 * Where [isMoved] is false (the editor's preview, which shows a song as it is written) the tabs are only named, a
 * staff that names no strings having the standard ones written in front of it, and nothing is moved or hidden.
 */
internal fun tabPlayerOf(
    instrument: UserPreferences.Instrument,
    tuning: InstrumentTuning?,
    notation: ChordNotation,
    isMoved: Boolean,
) = TabPlayer(
    selectors = selectorsOf(instrument),
    tuning = tuning?.takeIf { isMoved && instrument.hasFrets }?.let { TabTuning(instrument = instrument.tabInstrument, strings = it.strings) },
    hidesTabs = isMoved && !instrument.hasFrets && instrument != UserPreferences.Instrument.OTHER,
    assumedTuning = { notes, selector ->
        // A selector says who the tab is for, whatever its staff names; without one the names tell the instruments
        // with as many strings apart, where they are the open strings of one of them.
        val candidates = listOfNotNull(instrument.takeIf { it.hasFrets }, likelyInstruments[notes.size]) + UserPreferences.Instrument.entries.filter { it.hasFrets }
        val tunings = candidates.mapNotNull { candidate -> candidate.standardTabTuning?.takeIf { it.strings.size == notes.size } }
        selector?.let(::instrumentOfSelector)?.standardTabTuning?.takeIf { it.strings.size == notes.size }
            ?: tunings.firstOrNull { tuning -> tuning.strings.zip(notes).all { (string, note) -> note == null || string.mod(SEMITONES_IN_OCTAVE) == note } }
            ?: tunings.firstOrNull()
    },
    stringName = { note -> noteName(note, notation) },
    notation = notation,
)

/**
 * The lines of the empty staff the editor writes for a new tab: the strings of [instrument] tuned to [tuning], or a
 * guitar's in standard tuning for an instrument a tab is not played on, which is the tab whoever reads the file is
 * most likely to play. Named in [notation], the one the editor's field is typed in, as the chords around it are: Save
 * writes them into the file in the standard one.
 */
internal fun emptyTabStaffOf(instrument: UserPreferences.Instrument, tuning: InstrumentTuning?, notation: ChordNotation) = ChordProTabs.emptyStaff(
    strings = tuning?.takeIf { instrument.hasFrets }?.strings ?: InstrumentTuning.GUITAR.strings,
    stringName = { note -> noteName(note, notation) },
)

/**
 * The empty staff the editor writes for a tab for [tabInstrument], whose id its selector names: the player's own strings
 * ([emptyTabStaffOf] of [instrument] tuned to [tuning]) where it is the instrument in their hands or where the tab names
 * none, and the standard tuning of [tabInstrument] otherwise, since the player's tuning is of another instrument.
 */
internal fun emptyTabStaffFor(
    tabInstrument: UserPreferences.Instrument?,
    instrument: UserPreferences.Instrument,
    tuning: InstrumentTuning?,
    notation: ChordNotation,
) = if (tabInstrument == null || tabInstrument == instrument) {
    emptyTabStaffOf(instrument, tuning, notation)
} else {
    emptyTabStaffOf(tabInstrument, tabInstrument.tunings.firstOrNull(), notation)
}

/**
 * The instruments a tab can be written for, in the order the editor's Tab button offers them: every one with frets,
 * each named by its own id in the selector (`{start_of_tab-ukulele}`), which [tabPlayerOf] then picks it out by.
 */
internal val tabInstruments get() = UserPreferences.Instrument.entries.filter { it.hasFrets }

/**
 * The names a tab's selector may call [instrument] by, the best match first: its own, then the instrument it is a
 * kind of, whose tab a guitar with more strings or a bass with five plays all the same (moved to its strings).
 */
private fun selectorsOf(instrument: UserPreferences.Instrument) = when (instrument) {
    UserPreferences.Instrument.GUITAR_SEVEN_STRING,
    UserPreferences.Instrument.GUITAR_EIGHT_STRING,
    UserPreferences.Instrument.GUITAR_TWELVE_STRING -> listOf(instrument.id, UserPreferences.Instrument.GUITAR.id)
    UserPreferences.Instrument.BASS_FIVE_STRING -> listOf(instrument.id, UserPreferences.Instrument.BASS.id)
    UserPreferences.Instrument.KEYBOARD -> listOf(instrument.id, PIANO_SELECTOR)
    UserPreferences.Instrument.OTHER -> emptyList()
    else -> listOf(instrument.id)
}

/** The instrument a tab's selector names: one of the instruments' own ids, or `piano`, which ChordPro's examples use. */
internal fun instrumentOfSelector(selector: String) = if (selector == PIANO_SELECTOR) UserPreferences.Instrument.KEYBOARD else UserPreferences.Instrument.fromId(selector)

/**
 * Whether a tab can be played on the instrument at all: the keyboard has no strings and the violin no frets, and Other
 * is an instrument nothing is known about, whose player is shown every tab as it is written.
 */
private val UserPreferences.Instrument.hasFrets
    get() = this != UserPreferences.Instrument.KEYBOARD && this != UserPreferences.Instrument.VIOLIN && this != UserPreferences.Instrument.OTHER

/**
 * What a tab of the instrument is for: the guitar for the twelve-string, whose courses are fingered as the guitar's
 * strings, so that a guitar tab is moved to it without a word; every other instrument's own id.
 */
private val UserPreferences.Instrument.tabInstrument get() = if (isTwelveString) UserPreferences.Instrument.GUITAR.id else id

private val UserPreferences.Instrument.standardTabTuning get() = tunings.firstOrNull()?.let { TabTuning(instrument = tabInstrument, strings = it.strings) }

/**
 * What a staff of so many strings is most likely written for, where neither its selector nor the reader's own
 * instrument says: the four and five string staves of a bass, the six of a guitar, and the seven and eight of a guitar
 * with as many. A ukulele's or a mandolin's tab names its strings, or is read by its own players, far more often than a
 * bass's, which is written by and for players who read nothing else.
 */
private val likelyInstruments = mapOf(
    4 to UserPreferences.Instrument.BASS,
    5 to UserPreferences.Instrument.BASS_FIVE_STRING,
    6 to UserPreferences.Instrument.GUITAR,
    7 to UserPreferences.Instrument.GUITAR_SEVEN_STRING,
    8 to UserPreferences.Instrument.GUITAR_EIGHT_STRING,
)

private const val PIANO_SELECTOR = "piano"
private const val SEMITONES_IN_OCTAVE = 12
