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

interface SetChordProCoverArtUseCase {

    /**
     * Makes [url] the cover image of a ChordPro document, leaving the rest of the text exactly as it was: the first
     * `{meta: cover …}` line is rewritten where it stands, or one is written into the header after the album. A null
     * [url], or one that is not an `http` or `https` address, removes the cover instead.
     */
    operator fun invoke(text: String, url: String?): String
}
