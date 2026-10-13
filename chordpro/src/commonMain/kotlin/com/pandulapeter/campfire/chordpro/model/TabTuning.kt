/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.model

/**
 * The open strings a tab is read in or moved to.
 *
 * @property instrument What the strings are of (`guitar`, `bass`, `ukulele`…), the same name a tab's selector names
 *   it by, so that a tab is moved without a word where it is written for the instrument it is read on, and the reader is
 *   told where it is written for another one. Only ever compared and handed back, never read.
 * @property strings The open strings as MIDI note numbers, in the order a tuning is written: the string at the bottom of a
 *   tab (the guitar's low E) first, and the one at its top last. The octaves are real: they say whether a tab moved to
 *   them is moved up or down.
 */
public data class TabTuning(
    val instrument: String,
    val strings: List<Int>,
)
