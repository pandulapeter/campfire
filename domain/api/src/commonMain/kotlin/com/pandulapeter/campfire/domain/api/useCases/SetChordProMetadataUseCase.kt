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

import com.pandulapeter.campfire.chordpro.ChordProMetadataFields

interface SetChordProMetadataUseCase {

    /**
     * Makes each of [values] what a ChordPro document says for its field — its title, artist, album, year and the like
     * — leaving the rest of the text exactly as it was: the line a value is read from is rewritten where it stands, a
     * field the document lacks is written into its header, and a null or blank value removes the field. Fields missing
     * from [values] are left alone.
     */
    operator fun invoke(text: String, values: Map<ChordProMetadataFields.Field, String?>): String
}
