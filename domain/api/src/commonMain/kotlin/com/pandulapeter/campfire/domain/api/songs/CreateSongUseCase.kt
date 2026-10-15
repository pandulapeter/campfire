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

import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields.Field
import com.pandulapeter.campfire.data.model.song.Song

interface CreateSongUseCase {

    /**
     * Writes a new song with its title, artist and optional metadata, and returns the song it became. The song is the
     * template the editor opens on, or [text] where one is given - a chord sheet pasted in - with the values set into
     * it, a blank one removing what the text declared for its field. Either way the file is named by the title and the
     * artist, numbered where the name is taken.
     */
    suspend operator fun invoke(
        title: String,
        artist: String,
        metadata: Map<Field, String> = emptyMap(),
        text: String? = null,
    ): Song
}
