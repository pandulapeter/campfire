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

interface DraftSongFromTextUseCase {

    /**
     * The text a chord sheet pasted, dropped or shared as a new song opens in the editor with, nothing written: [text]
     * with [placeholderTitle] and [placeholderArtist] for a title or an artist it does not declare, the new song
     * template's header lines it has none of, and formatted as an import is. What it declares, in its header or as a
     * change in its body, is left as it is.
     */
    operator fun invoke(text: String, placeholderTitle: String, placeholderArtist: String): String
}
