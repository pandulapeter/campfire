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
 * One line of a row a run of tablature is cut into, and whether it is a line of the staff. A piece of a staff line cut
 * short by a row (`--|`) need not look like one by itself any more, so whether it is one is decided on the whole line
 * before it is cut, and travels with every piece of it.
 */
public data class TabRowLine(
    val text: String,
    val isStaffLine: Boolean,
)
