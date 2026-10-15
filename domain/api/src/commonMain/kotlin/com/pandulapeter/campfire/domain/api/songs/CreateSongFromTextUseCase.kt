/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.songs

import com.pandulapeter.campfire.data.model.song.Song

interface CreateSongFromTextUseCase {

    /**
     * Writes [text] as a new song, exactly as it is, and returns the song it became: named by the title the text
     * declares (its subtitle included, as the library shows it) and its artist, numbered where the name is taken. The
     * caller makes sure the text declares a title; one that declares none is named as the file name rules name an empty
     * title.
     */
    suspend operator fun invoke(text: String): Song
}
