/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

/** What a list without content has in its place. */
enum class Placeholder {
    LOADING,
    ERROR,
    NO_SONGS,
    NO_SETLISTS,

    /** The library has songs, but every one of them is filtered out. */
    ALL_SONGS_HIDDEN,

    /** There are setlists, but every one of them is archived and the screen is not showing those. */
    ALL_SETLISTS_HIDDEN,

    NO_MATCHING_SONGS,
    NO_MATCHING_SETLISTS,
}
