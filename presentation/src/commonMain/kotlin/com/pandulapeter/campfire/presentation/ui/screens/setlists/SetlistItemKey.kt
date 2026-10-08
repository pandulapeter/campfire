/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.setlists

import kotlinx.coroutines.flow.first

/**
 * The lazy list key of a song inside a setlist, encoded as a string so that the list can save it.
 */
/** The grid key of one song inside one setlist, since a song can appear in several of them. */
internal class SetlistItemKey(val string: String?) {

    constructor(setlistFileName: String, songFileName: String) : this("$setlistFileName$TOKEN$songFileName")

    private val parts = string?.split(TOKEN)?.takeIf { it.size == 2 }

    val setlistFileName: String? = parts?.first()

    val songFileName: String? = parts?.last()

    companion object {
        // Contains characters no normalized name can hold, so it can never occur inside a setlist's file name.
        private const val TOKEN = "#*#"
    }
}
