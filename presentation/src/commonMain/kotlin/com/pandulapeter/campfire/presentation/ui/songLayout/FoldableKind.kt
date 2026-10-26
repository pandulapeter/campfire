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

/**
 * The ways of writing lines down that can be folded away, none of which says anything to somebody who only sings:
 * tablature, a chord grid, and the notation of an environment ChordPro hands to another program (`ChordProLine.Verbatim`).
 */
internal enum class FoldableKind {
    TAB,
    GRID,
    VERBATIM,
}
