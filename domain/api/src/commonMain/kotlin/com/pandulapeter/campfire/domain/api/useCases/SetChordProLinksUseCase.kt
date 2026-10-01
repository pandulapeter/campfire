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

import com.pandulapeter.campfire.chordpro.model.ChordProLink

interface SetChordProLinksUseCase {

    /**
     * Makes [links] the links of a ChordPro document, leaving every unrelated byte and each unchanged link line as
     * written. URLs are normalized as web addresses and kept once; new links follow the existing ones in the header.
     * Blank names are omitted, and names cannot introduce another directive or line into the file.
     */
    operator fun invoke(text: String, links: List<ChordProLink>): String
}
