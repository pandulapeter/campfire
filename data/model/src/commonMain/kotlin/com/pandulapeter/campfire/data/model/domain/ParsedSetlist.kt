/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

/**
 * A setlist document read for an import, before it is in the library. [setlist] always has a date, today's where the
 * document named none, and [isDated] says which of the two it is: a file with no day of its own says nothing about
 * the day of a library setlist it is compared with or replaces, so that one keeps its own.
 */
data class ParsedSetlist(
    val setlist: Setlist,
    val isDated: Boolean,
)
