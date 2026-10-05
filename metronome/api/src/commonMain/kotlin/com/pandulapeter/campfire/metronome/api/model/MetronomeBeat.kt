/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.metronome.api.model

/**
 * One click, as it is heard.
 *
 * @param beatIndex The beat of the bar it is on, from 0.
 * @param barIndex The bar since the click started (or since the bar was last restarted), from 0.
 * @param level How the beat sounds; a subdivision carries its beat's level, since a muted beat mutes its
 *   subdivisions too.
 * @param isSubdivision Whether it is one of the clicks between the beats rather than the beat itself.
 */
data class MetronomeBeat(
    val beatIndex: Int,
    val barIndex: Long,
    val level: BeatLevel,
    val isSubdivision: Boolean,
)
