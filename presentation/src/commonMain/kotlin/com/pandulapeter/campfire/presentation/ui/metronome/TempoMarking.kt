/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.metronome

/**
 * The Italian tempo marking a tempo falls under, shown under the Metronome tab's tempo. Not translated: it is notation,
 * written the same in every language a score is read in.
 */
internal fun tempoMarking(bpm: Int) = TEMPO_MARKINGS.last { bpm >= it.first }.second

private val TEMPO_MARKINGS = listOf(
    0 to "Grave",
    40 to "Largo",
    55 to "Adagio",
    70 to "Andante",
    90 to "Moderato",
    110 to "Allegro",
    140 to "Vivace",
    170 to "Presto",
    200 to "Prestissimo",
)

/** Every marking [tempoMarking] can name, for the room the widest of them takes. */
internal val TEMPO_MARKING_NAMES = TEMPO_MARKINGS.map { it.second }
