/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songInfo

import com.pandulapeter.campfire.chordpro.model.ChordProMetadata

private val ChordProMetadata.hasInfoRows
    get() = listOf(album, year, composer, lyricist, duration).any { !it.isNullOrBlank() }

/** Whether the song says anything about itself beyond how it is played, which is what the card and the sheet hold. */
internal val ChordProMetadata.hasSongInfo
    get() = hasInfoRows || tags.isNotEmpty() || languages.isNotEmpty() || links.isNotEmpty()
