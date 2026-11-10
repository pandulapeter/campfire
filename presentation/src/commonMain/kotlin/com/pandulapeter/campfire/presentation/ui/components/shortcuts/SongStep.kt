/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.shortcuts

/**
 * What a song's step keys (the arrows, Page Up, and Page Down) move it by, as Preferences' Layout and Turn by set it, so
 * that the shortcuts sheet says what they do in the setup the reader has rather than in every one of them.
 */
internal enum class SongStep {
    /** In pages turned a page at a time. */
    PAGE,

    /** In pages turned a column at a time, a strip moving sideways. */
    COLUMN,

    /** In one column scrolling by itself, which a step only nudges. */
    NUDGE,
}
