/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.scales

/**
 * A chord a [ScaleGroup]'s home scale leaves out, with the scales to play over it while it sounds: a secondary dominant,
 * a borrowed chord, a minor song's major fifth; from [SongScaleSuggestions], also a dominant whose third the group's
 * first scale lacks, a blues's V7. Its scales hold every note of the chord, so a viewer can offer them as they are.
 *
 * @property chordName The chord as the song first writes it, in the standard notation (`E7`, `D/F#`).
 * @property rootName The note [scales] are played on, in the standard notation: the group's root where a minor scale
 *   of the group's own key holds the chord, the minor chord a secondary dominant leads into where its harmonic minor
 *   is played, else the chord's root.
 * @property scales The scales to play over the chord, the first the best answer.
 * @property otherChordNames From [SongScaleSuggestions], the other chords the same scale on [rootName] is played over,
 *   as the song first writes them, in play order: a song's V and V7 (`F#`, `F#7`) are one change of scale, not two, and
 *   so is a ii–V of another key, said in that key (`F#m7b5` and `D7` in C are G major).
 */
public data class ScaleException(
    val chordName: String,
    val rootName: String,
    val scales: List<Scale>,
    val otherChordNames: List<String> = emptyList(),
)
