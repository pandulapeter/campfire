/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields.Field
import com.pandulapeter.campfire.data.model.domain.Song

interface DuplicateSongUseCase {

    /**
     * Writes a new song with the text the file [fileName] holds now, with each field of [changes] set to its value (a
     * blank one removing the field), under a free file name derived from the copy's own title and artist, and returns
     * the song it became. Null where [fileName] is gone or could not be read, in which case nothing is written.
     */
    suspend operator fun invoke(fileName: String, changes: Map<Field, String>): Song?
}
