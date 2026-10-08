/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songLayout

import com.pandulapeter.campfire.chordpro.model.ChordProLine

/** True when every line that says anything is of the given kind, blank lines inside the run notwithstanding. */
internal inline fun <reified T : ChordProLine> List<ChordProLine>.areAll() =
    any { it is T } && all { it is T || it == ChordProLine.Blank }
